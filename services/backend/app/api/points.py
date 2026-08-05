"""积分账户 + 流水。"""
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, require_parent
from app.models import PointAccount, PointTransaction, User, UserRole
from app.schemas.point import (
    FamilyPointAccountOut,
    PointAccountOut,
    PointMeOut,
    PointTransactionOut,
)

router = APIRouter(prefix="/api/points", tags=["points"])


async def _get_or_create_account(db: AsyncSession, user_id: UUID) -> PointAccount:
    result = await db.execute(
        select(PointAccount).where(PointAccount.user_id == user_id)
    )
    account = result.scalar_one_or_none()
    if account is None:
        account = PointAccount(user_id=user_id, balance=0)
        db.add(account)
        await db.flush()
    return account


@router.get("/me", response_model=PointMeOut)
async def get_my_points(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    account = await _get_or_create_account(db, user.id)
    recent_stmt = (
        select(PointTransaction)
        .where(PointTransaction.user_id == user.id)
        .order_by(PointTransaction.created_at.desc())
        .limit(10)
    )
    recent = (await db.execute(recent_stmt)).scalars().all()
    return PointMeOut(balance=account.balance, recent=recent)


@router.get("/transactions", response_model=list[PointTransactionOut])
async def list_transactions(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = (
        select(PointTransaction)
        .where(PointTransaction.user_id == user.id)
        .order_by(PointTransaction.created_at.desc())
        .limit(limit)
        .offset(offset)
    )
    result = await db.execute(stmt)
    return result.scalars().all()


@router.get("/accounts/{user_id}", response_model=PointAccountOut)
async def get_account(
    user_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # 跨家庭不可见：通过 user.family_id 校验
    if user_id != user.id and not (
        {UserRole.parent, UserRole.family_admin} & set(user.roles)
    ):
        return PointAccountOut(user_id=user_id, balance=0)
    # 家长访问其他家庭成员时，需要确认该 user_id 在自己家庭里
    target_family = user.family_id
    if user_id != user.id:
        result = await db.execute(select(User).where(User.id == user_id))
        target = result.scalar_one_or_none()
        if not target or target.family_id != user.family_id:
            return PointAccountOut(user_id=user_id, balance=0)
    account = await _get_or_create_account(db, user_id)
    return account


@router.get("/family", response_model=list[FamilyPointAccountOut])
async def list_family_accounts(
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    """返回本家庭所有活跃成员的积分账户（家长可见全家，孩子 403）。"""
    if parent.family_id is None:
        return []
    stmt = (
        select(User, PointAccount)
        .outerjoin(PointAccount, PointAccount.user_id == User.id)
        .where(User.family_id == parent.family_id, User.is_active.is_(True))
        .order_by(User.created_at)
    )
    result = await db.execute(stmt)
    rows = result.all()
    return [
        FamilyPointAccountOut(
            user_id=u.id,
            username=u.username,
            display_name=u.display_name or "",
            roles=[r.value for r in u.roles],
            balance=a.balance if a else 0,
        )
        for u, a in rows
    ]
