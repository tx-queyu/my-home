"""Pydantic 模型 —— 单词相关。"""
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class WordOut(BaseModel):
    id: UUID
    course_id: UUID
    spelling: str = Field(min_length=1, max_length=64)
    syllables: list[str] = Field(default_factory=list)
    meaning_cn: str | None = None
    phonetic: str | None = None
    sample_sentence: str | None = None
    sample_sentence_translation: str | None = None
    sort_order: int = Field(ge=0, le=9999)
    is_active: bool = True
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}
