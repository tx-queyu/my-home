"""Pydantic 模型 —— 课程相关。"""
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class CourseOut(BaseModel):
    id: UUID
    subject: str = Field(min_length=1, max_length=32)
    textbook: str = Field(min_length=1, max_length=64)
    learning_method: str = Field(min_length=1, max_length=32)
    description: str | None = None
    default_points: int = Field(ge=0, le=1000)
    is_active: bool = True
    sort_order: int = Field(ge=0, le=9999)
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class CourseExperienceRequest(BaseModel):
    child_id: UUID | None = None


class CourseExperienceResult(BaseModel):
    task_title: str
    points_earned: int
    child_username: str
