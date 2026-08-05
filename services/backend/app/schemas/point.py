"""积分账户 + 流水 Pydantic 模型。"""
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.models import PointSource


class PointAccountOut(BaseModel):
    user_id: UUID
    balance: int

    model_config = {"from_attributes": True}


class PointTransactionOut(BaseModel):
    id: UUID
    user_id: UUID
    delta: int
    source: PointSource
    ref_id: UUID | None = None
    note: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class PointMeOut(BaseModel):
    balance: int
    recent: list[PointTransactionOut]


class FamilyPointAccountOut(BaseModel):
    user_id: UUID
    username: str
    display_name: str
    roles: list[str]
    balance: int
