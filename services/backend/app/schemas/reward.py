"""奖励 + 兑换 Pydantic 模型。"""
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field

from app.models import RedemptionStatus


class RewardBase(BaseModel):
    name: str = Field(min_length=1, max_length=64)
    description: str | None = Field(default=None, max_length=4000)
    cost: int = Field(ge=1, le=100000)
    stock: int | None = Field(default=None, ge=0)
    is_active: bool = True


class RewardCreate(RewardBase):
    pass


class RewardUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=64)
    description: str | None = Field(default=None, max_length=4000)
    cost: int | None = Field(default=None, ge=1, le=100000)
    stock: int | None = Field(default=None, ge=0)
    is_active: bool | None = None


class RewardOut(RewardBase):
    id: UUID
    family_id: UUID
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class RedemptionCreate(BaseModel):
    reward_id: UUID


class RedemptionOut(BaseModel):
    id: UUID
    family_id: UUID
    user_id: UUID
    reward_id: UUID
    reward_name: str | None = None
    cost: int
    status: RedemptionStatus
    handled_at: datetime | None = None
    handled_by: UUID | None = None
    created_at: datetime

    model_config = {"from_attributes": True}
