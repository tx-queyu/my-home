"""积分账户 helper —— 跨 router 共享。"""
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import PointAccount


async def get_or_create_account(db: AsyncSession, user_id: UUID) -> PointAccount:
    """读取或新建用户积分账户（balance=0 起步）。"""
    result = await db.execute(
        select(PointAccount).where(PointAccount.user_id == user_id)
    )
    account = result.scalar_one_or_none()
    if account is None:
        account = PointAccount(user_id=user_id, balance=0)
        db.add(account)
        await db.flush()
    return account
