"""学科成绩 API（v0.17.0）—— 家长录入/管理，全家可查看。

- GET /api/grades                    双视角列表（家长看全家/可按 user_id 过滤，孩子只看自己）
- POST /api/grades                   家长录入（assignee 必须在本家庭）
- PUT /api/grades/{id}               家长修改
- DELETE /api/grades/{id}            家长删除
- 汇总（按学科平均分等）由客户端内存聚合，不做端点（家庭数据量百级）
- 跨家庭访问返回 404 grade_not_found（不暴露存在性）
"""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, require_parent
from app.models import Grade, User, UserRole
from app.schemas.grade import GradeCreate, GradeOut, GradeUpdate

router = APIRouter(prefix="/api/grades", tags=["grades"])


async def _get_owned_grade(db: AsyncSession, grade_id: UUID, family_id: UUID | None) -> Grade:
    if family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="grade_not_found")
    result = await db.execute(
        select(Grade).where(Grade.id == grade_id, Grade.family_id == family_id)
    )
    grade = result.scalar_one_or_none()
    if not grade:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="grade_not_found")
    return grade


async def _validate_assignee(
    db: AsyncSession, assignee_user_id: UUID, family_id: UUID | None
) -> None:
    """成绩归属成员必须在本家庭（跨家庭/不存在 → 404，不暴露存在性）。"""
    if family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="assignee_not_found")
    result = await db.execute(
        select(User).where(
            User.id == assignee_user_id,
            User.family_id == family_id,
            User.is_active.is_(True),
        )
    )
    if result.scalar_one_or_none() is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="assignee_not_found")


@router.get("", response_model=list[GradeOut])
async def list_grades(
    user_id: UUID | None = Query(None),
    subject: str | None = Query(None, max_length=32),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if {UserRole.parent, UserRole.family_admin} & set(user.roles):
        # 家长：不传 user_id 看全家，传则看指定成员
        target_user_id = user_id
    else:
        # 孩子：只能看自己
        if user_id is not None and user_id != user.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="parent_only")
        target_user_id = user.id
    if user.family_id is None:
        return []
    stmt = select(Grade).where(Grade.family_id == user.family_id)
    if target_user_id is not None:
        stmt = stmt.where(Grade.assignee_user_id == target_user_id)
    if subject is not None:
        stmt = stmt.where(Grade.subject == subject)
    stmt = stmt.order_by(Grade.exam_date.desc(), Grade.created_at.desc())
    result = await db.execute(stmt)
    return result.scalars().all()


@router.post("", response_model=GradeOut, status_code=status.HTTP_201_CREATED)
async def create_grade(
    data: GradeCreate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    if parent.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    await _validate_assignee(db, data.assignee_user_id, parent.family_id)
    grade = Grade(
        family_id=parent.family_id,
        subject=data.subject,
        score=data.score,
        score_full=data.score_full,
        exam_name=data.exam_name,
        exam_date=data.exam_date,
        note=data.note,
        assignee_user_id=data.assignee_user_id,
        created_by=parent.id,
    )
    db.add(grade)
    await db.commit()
    await db.refresh(grade)
    return grade


@router.put("/{grade_id}", response_model=GradeOut)
async def update_grade(
    grade_id: UUID,
    data: GradeUpdate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    grade = await _get_owned_grade(db, grade_id, parent.family_id)
    if data.assignee_user_id is not None and data.assignee_user_id != grade.assignee_user_id:
        await _validate_assignee(db, data.assignee_user_id, parent.family_id)
        grade.assignee_user_id = data.assignee_user_id
    if data.subject is not None:
        grade.subject = data.subject
    if data.score is not None:
        grade.score = data.score
    if data.score_full is not None:
        grade.score_full = data.score_full
    if data.exam_name is not None:
        grade.exam_name = data.exam_name
    if data.exam_date is not None:
        grade.exam_date = data.exam_date
    if data.note is not None:
        grade.note = data.note
    # 组合校验：部分更新后 score 不得超 score_full（如只改满分为 50 而原分 92）
    if grade.score > grade.score_full:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="score_exceeds_full")
    await db.commit()
    await db.refresh(grade)
    return grade


@router.delete("/{grade_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_grade(
    grade_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    grade = await _get_owned_grade(db, grade_id, parent.family_id)
    await db.delete(grade)
    await db.commit()
