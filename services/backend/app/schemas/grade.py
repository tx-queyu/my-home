"""学科成绩相关 Pydantic 模型。"""
from datetime import date, datetime
from uuid import UUID

from pydantic import BaseModel, Field, model_validator


class GradeCreate(BaseModel):
    subject: str = Field(min_length=1, max_length=32)
    score: float = Field(ge=0)
    score_full: float = Field(default=100, ge=1)
    exam_name: str | None = Field(default=None, max_length=128)
    exam_date: date
    note: str | None = Field(default=None, max_length=256)
    assignee_user_id: UUID

    @model_validator(mode="after")
    def _check_score_range(self):
        if self.score > self.score_full:
            raise ValueError("score_exceeds_full")
        return self


class GradeUpdate(BaseModel):
    """部分更新：None = 不改（照搬 TaskUpdate 模式）。"""

    subject: str | None = Field(default=None, min_length=1, max_length=32)
    score: float | None = Field(default=None, ge=0)
    score_full: float | None = Field(default=None, ge=1)
    exam_name: str | None = Field(default=None, max_length=128)
    exam_date: date | None = None
    note: str | None = Field(default=None, max_length=256)
    assignee_user_id: UUID | None = None

    @model_validator(mode="after")
    def _check_score_range(self):
        # 更新模式下 None = 不改：用「新值 or 旧值」组合校验由 API 层在应用后复核
        if self.score is not None and self.score_full is not None and self.score > self.score_full:
            raise ValueError("score_exceeds_full")
        return self


class GradeOut(BaseModel):
    id: UUID
    family_id: UUID
    subject: str
    score: float
    score_full: float
    exam_name: str | None = None
    exam_date: date
    note: str | None = None
    assignee_user_id: UUID
    assignee_username: str | None = None
    created_by: UUID | None = None
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}
