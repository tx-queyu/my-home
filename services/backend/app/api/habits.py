"""习惯打卡：家长建习惯 + 家庭成员每日打卡（streak 递增积分）。"""
from datetime import date, timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.points import get_or_create_account
from app.core.security import get_current_user, require_parent
from app.models import (
    Habit,
    HabitLog,
    PointSource,
    PointTransaction,
    User,
    UserRole,
)
from app.schemas.habit import HabitCreate, HabitLogOut, HabitOut, HabitUpdate

router = APIRouter(prefix="/api/habits", tags=["habits"])


async def _get_owned_habit(db: AsyncSession, habit_id: UUID, family_id: UUID | None) -> Habit:
    if family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="habit_not_found")
    result = await db.execute(
        select(Habit).where(Habit.id == habit_id, Habit.family_id == family_id)
    )
    habit = result.scalar_one_or_none()
    if not habit:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="habit_not_found")
    return habit


async def _streak_states(
    db: AsyncSession, habit_id: UUID, user_id: UUID, today: date
) -> tuple[int, bool]:
    """当前连续天数 + 今日是否已打卡。

    今日已打 → (今日 streak_count, True)；未打 → (昨日 streak_count, False)，
    即「若现在打卡将连续 N+1 天」的基准。
    """
    result = await db.execute(
        select(HabitLog.streak_count).where(
            HabitLog.habit_id == habit_id,
            HabitLog.user_id == user_id,
            HabitLog.checkin_date == today,
        )
    )
    today_streak = result.scalar_one_or_none()
    if today_streak is not None:
        return today_streak, True
    result = await db.execute(
        select(HabitLog.streak_count).where(
            HabitLog.habit_id == habit_id,
            HabitLog.user_id == user_id,
            HabitLog.checkin_date == today - timedelta(days=1),
        )
    )
    yesterday_streak = result.scalar_one_or_none()
    return (yesterday_streak or 0), False


def _serialize_habit(
    habit: Habit, streak_state: tuple[int, bool] | None = None
) -> HabitOut:
    out = HabitOut.model_validate(habit)
    if streak_state is not None:
        out.current_streak, out.today_checked_in = streak_state
    return out


@router.get("", response_model=list[HabitOut])
async def list_habits(
    include_inactive: bool = Query(False),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        return []
    stmt = select(Habit).where(Habit.family_id == user.family_id)
    if not include_inactive:
        stmt = stmt.where(Habit.is_active.is_(True))
    stmt = stmt.order_by(Habit.created_at.asc())
    result = await db.execute(stmt)
    habits = result.scalars().all()
    today = date.today()
    return [
        _serialize_habit(h, await _streak_states(db, h.id, user.id, today)) for h in habits
    ]


@router.post("", response_model=HabitOut, status_code=status.HTTP_201_CREATED)
async def create_habit(
    data: HabitCreate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    if parent.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    # 先查重：避免 db.add 后的查询触发 autoflush 抛 IntegrityError（难捕获）
    existing = await db.execute(
        select(Habit).where(
            Habit.family_id == parent.family_id, Habit.name == data.name
        )
    )
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="habit_name_taken")
    habit = Habit(
        family_id=parent.family_id,
        name=data.name,
        points=data.points,
        streak_cap=data.streak_cap,
        is_active=data.is_active,
    )
    db.add(habit)
    try:
        await db.commit()
    except IntegrityError:
        # 兜底：两个并发请求同时通过预检查
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="habit_name_taken")
    await db.refresh(habit)
    return _serialize_habit(habit, (0, False))


# ↓ 注意：logs 路径必须在 /{habit_id} 之前注册，避免被 {habit_id} 匹配。
@router.get("/logs", response_model=list[HabitLogOut])
async def list_habit_logs(
    user_id: UUID | None = Query(None),
    date_from: date | None = Query(None),
    date_to: date | None = Query(None),
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
    # 跨家庭访问检查：通过 habit join 校验 family_id
    stmt = (
        select(HabitLog)
        .join(Habit, Habit.id == HabitLog.habit_id)
        .where(Habit.family_id == user.family_id)
        .order_by(HabitLog.checkin_date.desc(), HabitLog.created_at.desc())
    )
    if target_user_id is not None:
        stmt = stmt.where(HabitLog.user_id == target_user_id)
    if date_from is not None:
        stmt = stmt.where(HabitLog.checkin_date >= date_from)
    if date_to is not None:
        stmt = stmt.where(HabitLog.checkin_date <= date_to)
    result = await db.execute(stmt)
    return result.scalars().all()


@router.get("/{habit_id}", response_model=HabitOut)
async def get_habit(
    habit_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    habit = await _get_owned_habit(db, habit_id, user.family_id)
    state = await _streak_states(db, habit.id, user.id, date.today())
    return _serialize_habit(habit, state)


@router.post("/{habit_id}/log", response_model=HabitLogOut, status_code=status.HTTP_201_CREATED)
async def check_in(
    habit_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    habit = await _get_owned_habit(db, habit_id, user.family_id)
    if not habit.is_active:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="habit_inactive")

    today = date.today()

    # 先查重：避免 db.add(log) 后的 SELECT 触发 autoflush 抛 IntegrityError（难捕获）
    result = await db.execute(
        select(HabitLog).where(
            HabitLog.habit_id == habit.id,
            HabitLog.user_id == user.id,
            HabitLog.checkin_date == today,
        )
    )
    if result.scalar_one_or_none() is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="already_checked_in_today"
        )

    # streak：昨日有打卡则连续 +1，否则重新从 1 开始（断签归零）
    result = await db.execute(
        select(HabitLog.streak_count).where(
            HabitLog.habit_id == habit.id,
            HabitLog.user_id == user.id,
            HabitLog.checkin_date == today - timedelta(days=1),
        )
    )
    yesterday_streak = result.scalar_one_or_none()
    streak = (yesterday_streak + 1) if yesterday_streak is not None else 1
    points_earned = min(streak, habit.streak_cap) * habit.points

    # 先取/建账户（在 db.add(log) 之前，避免后续查询触发 autoflush 提前 INSERT log）
    account = await get_or_create_account(db, user.id)
    note = f"{habit.name}(连续{streak}天)"
    log = HabitLog(
        habit_id=habit.id,
        user_id=user.id,
        streak_count=streak,
        points_earned=points_earned,
        checkin_date=today,
        note=note,
    )
    db.add(log)
    account.balance += points_earned
    db.add(
        PointTransaction(
            user_id=user.id,
            delta=points_earned,
            source=PointSource.checkin,
            ref_id=habit.id,
            note=note,
        )
    )
    try:
        await db.commit()
    except IntegrityError:
        # 兜底：两个并发请求同时通过预检查
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="already_checked_in_today"
        )
    await db.refresh(log)
    return log


@router.put("/{habit_id}", response_model=HabitOut)
async def update_habit(
    habit_id: UUID,
    data: HabitUpdate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    habit = await _get_owned_habit(db, habit_id, parent.family_id)
    if data.name is not None:
        # 先查重（同 create）
        if data.name != habit.name:
            existing = await db.execute(
                select(Habit).where(
                    Habit.family_id == parent.family_id, Habit.name == data.name
                )
            )
            if existing.scalar_one_or_none() is not None:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT, detail="habit_name_taken"
                )
        habit.name = data.name
    if data.points is not None:
        habit.points = data.points
    if data.streak_cap is not None:
        habit.streak_cap = data.streak_cap
    if data.is_active is not None:
        habit.is_active = data.is_active
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="habit_name_taken")
    await db.refresh(habit)
    return _serialize_habit(habit)


@router.delete("/{habit_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_habit(
    habit_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    habit = await _get_owned_habit(db, habit_id, parent.family_id)
    await db.delete(habit)
    await db.commit()
