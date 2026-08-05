"""奖励 CRUD + 兑换 + 状态流转。"""
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, require_parent
from app.models import (
    PointAccount,
    PointSource,
    PointTransaction,
    Redemption,
    RedemptionStatus,
    Reward,
    User,
    UserRole,
)
from app.schemas.reward import (
    RedemptionCreate,
    RedemptionOut,
    RewardCreate,
    RewardOut,
    RewardUpdate,
)

router = APIRouter(prefix="/api", tags=["rewards"])


# ============================================================
# Rewards CRUD
# ============================================================
async def _get_owned_reward(db: AsyncSession, reward_id: UUID, family_id: UUID | None) -> Reward:
    if family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="reward_not_found")
    result = await db.execute(
        select(Reward).where(Reward.id == reward_id, Reward.family_id == family_id)
    )
    reward = result.scalar_one_or_none()
    if not reward:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="reward_not_found")
    return reward


@router.get("/rewards", response_model=list[RewardOut])
async def list_rewards(
    include_inactive: bool = Query(False),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        return []
    stmt = select(Reward).where(Reward.family_id == user.family_id)
    if not include_inactive:
        stmt = stmt.where(Reward.is_active.is_(True))
    stmt = stmt.order_by(Reward.cost.asc(), Reward.created_at.desc())
    result = await db.execute(stmt)
    return result.scalars().all()


@router.post("/rewards", response_model=RewardOut, status_code=status.HTTP_201_CREATED)
async def create_reward(
    data: RewardCreate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    if parent.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    reward = Reward(
        family_id=parent.family_id,
        name=data.name,
        description=data.description,
        cost=data.cost,
        stock=data.stock,
        is_active=data.is_active,
    )
    db.add(reward)
    await db.commit()
    await db.refresh(reward)
    return reward


@router.get("/rewards/{reward_id}", response_model=RewardOut)
async def get_reward(
    reward_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await _get_owned_reward(db, reward_id, user.family_id)


@router.put("/rewards/{reward_id}", response_model=RewardOut)
async def update_reward(
    reward_id: UUID,
    data: RewardUpdate,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    reward = await _get_owned_reward(db, reward_id, parent.family_id)
    if data.name is not None:
        reward.name = data.name
    if data.description is not None:
        reward.description = data.description
    if data.cost is not None:
        reward.cost = data.cost
    if data.stock is not None:
        reward.stock = data.stock
    if data.is_active is not None:
        reward.is_active = data.is_active
    await db.commit()
    await db.refresh(reward)
    return reward


@router.delete("/rewards/{reward_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_reward(
    reward_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    reward = await _get_owned_reward(db, reward_id, parent.family_id)
    await db.delete(reward)
    await db.commit()


# ============================================================
# Redemptions 兑换 + 状态流转
# ============================================================
async def _get_owned_redemption(
    db: AsyncSession, redemption_id: UUID, family_id: UUID | None
) -> Redemption:
    if family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="redemption_not_found")
    result = await db.execute(
        select(Redemption).where(
            Redemption.id == redemption_id, Redemption.family_id == family_id
        )
    )
    redemption = result.scalar_one_or_none()
    if not redemption:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="redemption_not_found")
    return redemption


@router.get("/redemptions", response_model=list[RedemptionOut])
async def list_redemptions(
    status_filter: RedemptionStatus | None = Query(None, alias="status"),
    user_id: UUID | None = Query(None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        return []
    target_user_id = user.id
    if {UserRole.parent, UserRole.family_admin} & set(user.roles):
        # 家长/家庭管理员可加 ?user_id= 看指定成员；不加则看全家
        if user_id is not None:
            target_user_id = user_id
        else:
            target_user_id = None  # None = 全家
    else:
        # 孩子只能看自己
        if user_id is not None and user_id != user.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="parent_only")

    stmt = select(Redemption).where(Redemption.family_id == user.family_id)
    if target_user_id is not None:
        stmt = stmt.where(Redemption.user_id == target_user_id)
    if status_filter is not None:
        stmt = stmt.where(Redemption.status == status_filter)
    stmt = stmt.order_by(Redemption.created_at.desc())
    result = await db.execute(stmt)
    redemptions = result.scalars().all()

    # 关联 reward 名称（避免 N+1）
    reward_ids = {r.reward_id for r in redemptions}
    reward_names: dict[UUID, str] = {}
    if reward_ids:
        reward_result = await db.execute(
            select(Reward.id, Reward.name).where(Reward.id.in_(reward_ids))
        )
        reward_names = {rid: name for rid, name in reward_result.all()}

    return [
        RedemptionOut(
            id=r.id,
            family_id=r.family_id,
            user_id=r.user_id,
            reward_id=r.reward_id,
            reward_name=reward_names.get(r.reward_id),
            cost=r.cost,
            status=r.status,
            handled_at=r.handled_at,
            handled_by=r.handled_by,
            created_at=r.created_at,
        )
        for r in redemptions
    ]


@router.post("/redemptions", response_model=RedemptionOut, status_code=status.HTTP_201_CREATED)
async def create_redemption(
    data: RedemptionCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    reward = await _get_owned_reward(db, data.reward_id, user.family_id)
    if not reward.is_active:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="reward_not_found")
    if reward.stock is not None and reward.stock <= 0:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="reward_out_of_stock")

    cost = reward.cost
    # 原子扣积分：UPDATE ... WHERE balance >= cost，影响 0 行 → 409 insufficient_points
    result = await db.execute(
        update(PointAccount)
        .where(PointAccount.user_id == user.id, PointAccount.balance >= cost)
        .values(balance=PointAccount.balance - cost)
    )
    if result.rowcount == 0:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="insufficient_points")

    redemption = Redemption(
        family_id=user.family_id,
        user_id=user.id,
        reward_id=reward.id,
        cost=cost,
        status=RedemptionStatus.pending,
    )
    db.add(redemption)
    await db.flush()  # 拿到 redemption.id
    db.add(
        PointTransaction(
            user_id=user.id,
            delta=-cost,
            source=PointSource.redemption,
            ref_id=redemption.id,
            note=reward.name,
        )
    )
    await db.commit()
    await db.refresh(redemption)
    return RedemptionOut(
        id=redemption.id,
        family_id=redemption.family_id,
        user_id=redemption.user_id,
        reward_id=redemption.reward_id,
        reward_name=reward.name,
        cost=redemption.cost,
        status=redemption.status,
        handled_at=redemption.handled_at,
        handled_by=redemption.handled_by,
        created_at=redemption.created_at,
    )


@router.post("/redemptions/{redemption_id}/fulfill", response_model=RedemptionOut)
async def fulfill_redemption(
    redemption_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    redemption = await _get_owned_redemption(db, redemption_id, parent.family_id)
    if redemption.status != RedemptionStatus.pending:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="redemption_status_invalid"
        )
    redemption.status = RedemptionStatus.fulfilled
    redemption.handled_at = datetime.now(timezone.utc)
    redemption.handled_by = parent.id
    await db.commit()
    await db.refresh(redemption)
    return redemption


@router.post("/redemptions/{redemption_id}/reject", response_model=RedemptionOut)
async def reject_redemption(
    redemption_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    redemption = await _get_owned_redemption(db, redemption_id, parent.family_id)
    if redemption.status != RedemptionStatus.pending:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="redemption_status_invalid"
        )
    # 退还积分
    account_result = await db.execute(
        select(PointAccount).where(PointAccount.user_id == redemption.user_id)
    )
    account = account_result.scalar_one_or_none()
    if account is None:
        # 不应该发生，但兜底
        account = PointAccount(user_id=redemption.user_id, balance=0)
        db.add(account)
        await db.flush()
    account.balance += redemption.cost
    db.add(
        PointTransaction(
            user_id=redemption.user_id,
            delta=redemption.cost,
            source=PointSource.adjustment,
            ref_id=redemption.id,
            note="兑换拒绝退还",
        )
    )
    redemption.status = RedemptionStatus.rejected
    redemption.handled_at = datetime.now(timezone.utc)
    redemption.handled_by = parent.id
    await db.commit()
    await db.refresh(redemption)
    return redemption
