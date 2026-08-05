"""家电相关 Pydantic 模型。"""
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field

from app.models import ApplianceStatus


class ApplianceBase(BaseModel):
    name: str = Field(min_length=1, max_length=64)
    type: str = Field(min_length=1, max_length=32)
    location: str = Field(min_length=1, max_length=64)
    status: ApplianceStatus = ApplianceStatus.normal
    notes: str | None = Field(default=None, max_length=2000)


class ApplianceCreate(ApplianceBase):
    pass


class ApplianceUpdate(ApplianceBase):
    pass


class ApplianceOut(ApplianceBase):
    id: UUID
    family_id: UUID
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}
