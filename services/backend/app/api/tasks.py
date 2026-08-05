"""任务 CRUD + 完成 + 记录撤销。"""
from datetime import date, datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.points import get_or_create_account
from app.core.security import get_current_user, require_parent
from app.models import (
    PointSource,
    PointTransaction,
    Task,
    TaskRecord,
    User,
    UserRole,
)
from app.schemas.task import (
    TaskCreate,
    TaskOut,
    TaskRecordOut,
    TaskUpdate,
)

router = APIRouter(prefix="/api/tasks", tags=["tasks"])


async def _get_owned_task(db: AsyncSession, task_id: UUID, family_id: UUID | None) -> Task:
    if family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="task_not_found")
    result = await db.execute(
        select(Task).where(Task.id == task_id, Task.family_id == family_id)
    )
    task = result.scalar_one_or_none()
    if not task:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="task_not_found")
    return task


async def _validate_assignee(
    db: AsyncSession, assignee_user_id: UUID | None, family_id: UUID
) -> None:
    """指派对象必须是本家庭的活跃孩子账号；跨家庭一律 404。"""
    if assignee_user_id is None:
        return
    result = await db.execute(
        select(User).where(
            User.id == assignee_user_id,
            User.family_id == family_id,
            User.is_active.is_(True),
        )
    )
    assignee = result.scalar_one_or_none()
    if assignee is None or UserRole.child not in assignee.roles:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="assignee_not_found")


def _serialize_task(task: Task, completed_today_ids: set[UUID] | None = None) -> TaskOut:
    out = TaskOut.model_validate(task)
    out.completed_today = bool(completed_today_ids) and task.id in completed_today_ids
    return out


async def _completed_today_ids(db: AsyncSession, user_id: UUID) -> set[UUID]:
    result = await db.execute(
        select(TaskRecord.task_id).where(
            TaskRecord.user_id == user_id,
            TaskRecord.completed_date == date.today(),
        )
    )
    return set(result.scalars().all())


@router.get("", response_model=list[TaskOut])
async def list_tasks(
    include_inactive: bool = Query(False),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        return []
    stmt = select(Task).where(Task.family_id == user.family_id)
    if not include_inactive:
        stmt = stmt.where(Task.is_active.is_(True))
    stmt = stmt.order_by(Task.due_date.asc().nulls_last(), Task.created_at.desc())
    result = await db.execute(stmt)
    tasks = result.scalars().all()
    done_ids = await _completed_today_ids(db, user.id)
    return [_serialize_task(t, done_ids) for t in tasks]


@router.post("", response_model=TaskOut, status_code=status.HTTP_201_CREATED)
async def create_task(
    data: TaskCreate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    if parent.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    await _validate_assignee(db, data.assignee_user_id, parent.family_id)
    task = Task(
        family_id=parent.family_id,
        course_id=data.course_id,
        title=data.title,
        description=data.description,
        points=data.points,
        due_date=data.due_date,
        is_active=data.is_active,
        assignee_user_id=data.assignee_user_id,
        available_start_date=data.available_start_date,
        available_end_date=data.available_end_date,
        available_start_time=data.available_start_time,
        available_end_time=data.available_end_time,
        recurrence_type=data.recurrence_type,
        recurrence_weekdays=data.recurrence_weekdays,
    )
    db.add(task)
    await db.commit()
    await db.refresh(task)
    return _serialize_task(task)


# ↓ 注意：records 路径必须在 /{task_id} 之前注册，避免被 {task_id} 匹配。
@router.get("/records", response_model=list[TaskRecordOut])
async def list_task_records(
    user_id: UUID | None = Query(None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if {UserRole.parent, UserRole.family_admin} & set(user.roles):
        # 家长：不传 user_id 看全家，传则看指定孩子
        target_user_id = user_id
    else:
        # 孩子：只能看自己
        if user_id is not None and user_id != user.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="parent_only")
        target_user_id = user.id
    # 跨家庭访问检查：通过 task join 校验 family_id
    stmt = (
        select(TaskRecord)
        .join(Task, Task.id == TaskRecord.task_id)
        .where(Task.family_id == user.family_id)
        .order_by(TaskRecord.created_at.desc())
    )
    if target_user_id is not None:
        stmt = stmt.where(TaskRecord.user_id == target_user_id)
    result = await db.execute(stmt)
    return result.scalars().all()


@router.delete("/records/{record_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_task_record(
    record_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    # 通过 task join 校验 family_id
    result = await db.execute(
        select(TaskRecord)
        .join(Task, Task.id == TaskRecord.task_id)
        .where(TaskRecord.id == record_id, Task.family_id == parent.family_id)
    )
    record = result.scalar_one_or_none()
    if not record:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="record_not_found")
    # 退积分
    account = await get_or_create_account(db, record.user_id)
    account.balance -= record.points_earned
    db.add(
        PointTransaction(
            user_id=record.user_id,
            delta=-record.points_earned,
            source=PointSource.adjustment,
            ref_id=record.task_id,
            note="家长撤销任务完成",
        )
    )
    await db.delete(record)
    await db.commit()


@router.post("/{task_id}/complete", response_model=TaskRecordOut, status_code=status.HTTP_201_CREATED)
async def complete_task(
    task_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    task = await _get_owned_task(db, task_id, user.family_id)
    if not task.is_active:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="task_inactive")

    # 指派检查
    if task.assignee_user_id is not None and task.assignee_user_id != user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="task_not_assigned_to_you")

    today = date.today()
    # 日期范围
    if task.available_start_date and today < task.available_start_date:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="task_not_started_yet")
    if task.available_end_date and today > task.available_end_date:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="task_expired")

    # 先查重：避免 db.add(record) 后的 SELECT 触发 autoflush 抛 IntegrityError（难捕获）
    if task.recurrence_type == "one_off":
        existing = await db.execute(
            select(TaskRecord).where(
                TaskRecord.task_id == task.id, TaskRecord.user_id == user.id
            )
        )
        if existing.scalar_one_or_none() is not None:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="task_already_completed")
    else:
        if task.recurrence_type == "weekly" and (
            not task.recurrence_weekdays
            or today.isoweekday() not in task.recurrence_weekdays
        ):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail="task_not_available_today"
            )
        existing_today = await db.execute(
            select(TaskRecord).where(
                TaskRecord.task_id == task.id,
                TaskRecord.user_id == user.id,
                TaskRecord.completed_date == today,
            )
        )
        if existing_today.scalar_one_or_none() is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT, detail="task_already_completed_today"
            )

    # 每日时间窗口
    if task.available_start_time and task.available_end_time:
        now = datetime.now().time()
        if now < task.available_start_time or now > task.available_end_time:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail="task_outside_time_window"
            )

    # 先取/建账户（在 db.add(record) 之前，避免后续查询触发 autoflush 提前 INSERT record）
    account = await get_or_create_account(db, user.id)
    record = TaskRecord(
        task_id=task.id,
        user_id=user.id,
        points_earned=task.points,
        completed_date=today,
    )
    db.add(record)
    account.balance += task.points
    db.add(
        PointTransaction(
            user_id=user.id,
            delta=task.points,
            source=PointSource.task,
            ref_id=task.id,
            note=task.title,
        )
    )
    try:
        await db.commit()
    except IntegrityError:
        # 兜底：两个并发请求同时通过预检查
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="task_already_completed")
    await db.refresh(record)
    return record


@router.get("/{task_id}", response_model=TaskOut)
async def get_task(
    task_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    task = await _get_owned_task(db, task_id, user.family_id)
    done_ids = await _completed_today_ids(db, user.id)
    return _serialize_task(task, done_ids)


@router.put("/{task_id}", response_model=TaskOut)
async def update_task(
    task_id: UUID,
    data: TaskUpdate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    task = await _get_owned_task(db, task_id, parent.family_id)
    if data.title is not None:
        task.title = data.title
    if data.description is not None:
        task.description = data.description
    if data.course_id is not None:
        task.course_id = data.course_id
    if data.points is not None:
        task.points = data.points
    if data.due_date is not None:
        task.due_date = data.due_date
    if data.is_active is not None:
        task.is_active = data.is_active
    if data.assignee_user_id is not None:
        await _validate_assignee(db, data.assignee_user_id, parent.family_id)
        task.assignee_user_id = data.assignee_user_id
    if data.available_start_date is not None:
        task.available_start_date = data.available_start_date
    if data.available_end_date is not None:
        task.available_end_date = data.available_end_date
    if data.available_start_time is not None:
        task.available_start_time = data.available_start_time
    if data.available_end_time is not None:
        task.available_end_time = data.available_end_time
    if data.recurrence_type is not None:
        task.recurrence_type = data.recurrence_type
    if data.recurrence_weekdays is not None:
        task.recurrence_weekdays = data.recurrence_weekdays
    await db.commit()
    await db.refresh(task)
    return _serialize_task(task)


@router.delete("/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_task(
    task_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    task = await _get_owned_task(db, task_id, parent.family_id)
    await db.delete(task)
    await db.commit()
