"""课程管理 — admin 只读 + 启停 + 体验。

用户端只读 API 见 `app/api/courses.py`（`GET /api/courses`）。
admin 端管理在本文件（`/api/system/courses`），权限 `require_admin`。

注意：Course 是系统预置目录（41 条种子覆盖 13 学科），不应该让 admin 在 app 内随意
新增/编辑/删除（会破坏种子目录一致性，调整目录应走 `core/seed_courses.py` + 迁移 SQL）。
因此本 router 只提供 list / activate / deactivate / experience（模拟完整任务流程）。
"""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.points import get_or_create_account
from app.core.security import require_admin
from app.models import (
    Course,
    PointTransaction,
    PointSource,
    Task,
    TaskRecord,
    User,
    UserRole,
)
from app.schemas.course import (
    CourseExperienceRequest,
    CourseExperienceResult,
    CourseOut,
)

router = APIRouter(prefix="/api/system/courses", tags=["system-courses"])


async def _get_course(db: AsyncSession, course_id: UUID) -> Course:
    result = await db.execute(select(Course).where(Course.id == course_id))
    course = result.scalar_one_or_none()
    if not course:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="course_not_found")
    return course


@router.get("", response_model=list[CourseOut])
async def list_admin_courses(
    subject: str | None = None,
    textbook: str | None = None,
    learning_method: str | None = None,
    include_inactive: bool = True,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Course)
    if not include_inactive:
        stmt = stmt.where(Course.is_active.is_(True))
    if subject:
        stmt = stmt.where(Course.subject == subject)
    if textbook:
        stmt = stmt.where(Course.textbook == textbook)
    if learning_method:
        stmt = stmt.where(Course.learning_method == learning_method)
    stmt = stmt.order_by(Course.subject, Course.textbook, Course.sort_order, Course.learning_method)
    result = await db.execute(stmt)
    return result.scalars().all()


@router.post("/{course_id}/activate", response_model=CourseOut)
async def activate_course(
    course_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    course = await _get_course(db, course_id)
    course.is_active = True
    await db.commit()
    await db.refresh(course)
    return course


@router.post("/{course_id}/deactivate", response_model=CourseOut)
async def deactivate_course(
    course_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    course = await _get_course(db, course_id)
    course.is_active = False
    await db.commit()
    await db.refresh(course)
    return course


@router.post("/{course_id}/experience", response_model=CourseExperienceResult)
async def experience_course(
    course_id: UUID,
    payload: CourseExperienceRequest,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """模拟一次完整任务流程：建 task → 标记完成 → 给孩子加积分。

    用于 admin 在课程管理页直观体验某个课程的积分流转。task 留在孩子任务历史里。
    """
    course = await _get_course(db, course_id)
    if not course.is_active:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="course_inactive",
        )
    # 解析目标孩子：优先 payload.child_id > admin 家庭内第一个孩子 > 全局第一个孩子
    if payload.child_id is not None:
        child_result = await db.execute(
            select(User).where(
                User.id == payload.child_id,
                User.roles.any(UserRole.child),
                User.is_active.is_(True),
            )
        )
        child = child_result.scalar_one_or_none()
        if child is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="child_not_found",
            )
    elif admin.family_id is not None:
        child_result = await db.execute(
            select(User)
            .where(
                User.family_id == admin.family_id,
                User.roles.any(UserRole.child),
                User.is_active.is_(True),
            )
            .order_by(User.created_at)
            .limit(1)
        )
        child = child_result.scalar_one_or_none()
    else:
        child_result = await db.execute(
            select(User)
            .where(
                User.roles.any(UserRole.child),
                User.is_active.is_(True),
            )
            .order_by(User.created_at)
            .limit(1)
        )
        child = child_result.scalar_one_or_none()
    if child is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="no_child_available",
        )
    if child.family_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="child_no_family",
        )
    # 建任务（title 由 textbook · learning_method 拼出，note 同）
    task_title = f"{course.textbook} · {course.learning_method}"
    task = Task(
        family_id=child.family_id,
        course_id=course.id,
        title=task_title,
        description=course.description,
        points=course.default_points,
        is_active=True,
    )
    db.add(task)
    await db.flush()  # 拿 task.id
    # 取/建账户（在 db.add(record) 之前，避免 autoflush 提前 INSERT record 触发唯一约束）
    account = await get_or_create_account(db, child.id)
    record = TaskRecord(task_id=task.id, user_id=child.id, points_earned=task.points)
    db.add(record)
    account.balance += task.points
    db.add(
        PointTransaction(
            user_id=child.id,
            delta=task.points,
            source=PointSource.task,
            ref_id=task.id,
            note=task.title,
        )
    )
    await db.commit()
    await db.refresh(task)
    return CourseExperienceResult(
        task_title=task.title,
        points_earned=task.points,
        child_username=child.username,
    )
