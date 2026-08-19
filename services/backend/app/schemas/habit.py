"""习惯打卡相关 Pydantic 模型。"""
from datetime import date, datetime
from uuid import UUID

from pydantic import BaseModel, Field


class HabitCreate(BaseModel):
    name: str = Field(min_length=1, max_length=64)
    points: int = Field(default=1, ge=1, le=100)
    streak_cap: int = Field(default=7, ge=1, le=365)
    is_active: bool = True


class HabitUpdate(BaseModel):
    """部分更新：None = 不改（照搬 TaskUpdate 模式）。"""

    name: str | None = Field(default=None, min_length=1, max_length=64)
    points: int | None = Field(default=None, ge=1, le=100)
    streak_cap: int | None = Field(default=None, ge=1, le=365)
    is_active: bool | None = None


class HabitOut(BaseModel):
    id: UUID
    family_id: UUID
    name: str
    points: int
    streak_cap: int
    is_active: bool
    # 序列化时注入（照搬 TaskOut.completed_today 模式）
    current_streak: int = 0
    today_checked_in: bool = False
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class HabitLogOut(BaseModel):
    id: UUID
    habit_id: UUID
    user_id: UUID
    habit_name: str | None = None
    username: str | None = None
    streak_count: int
    points_earned: int
    checkin_date: date
    note: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}
