"""学习时长会话相关 Pydantic 模型。"""
from datetime import date, datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field


class StudySessionCreate(BaseModel):
    subject: str = Field(min_length=1, max_length=32)
    textbook: str = Field(min_length=1, max_length=64)
    learning_method: str = Field(min_length=1, max_length=32)
    session_type: Literal["reading", "learn", "quiz"]
    source: Literal["task", "experience", "self_study"]
    duration_seconds: int = Field(ge=1, le=86400)
    # None = 今天（服务端补 date.today()）
    session_date: date | None = None


class StudySessionOut(BaseModel):
    id: UUID
    user_id: UUID
    subject: str
    textbook: str
    learning_method: str
    session_type: str
    source: str
    duration_seconds: int
    session_date: date
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class TextbookTimeOut(BaseModel):
    subject: str
    textbook: str
    total_seconds: int
    session_count: int


class StudyStatsOut(BaseModel):
    today_seconds: int
    week_seconds: int
    total_seconds: int
    by_textbook: list[TextbookTimeOut]
