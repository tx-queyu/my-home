"""Pydantic 模型 —— 家长自学教材。"""
from uuid import UUID

from pydantic import BaseModel, Field

from app.schemas.course import CourseOut


class SelfStudyTextbookCreate(BaseModel):
    subject: str = Field(min_length=1, max_length=32)
    textbook: str = Field(min_length=1, max_length=64)


class SelfStudyTextbookOut(BaseModel):
    """我的教材 + 该教材下 active 课程(前端按 sessionType 过滤互动课)。"""

    id: UUID
    subject: str
    textbook: str
    courses: list[CourseOut]


class TextbookOptionOut(BaseModel):
    """可添加的教材选项(系统 active 课程聚合成教材维度)。"""

    subject: str
    textbook: str
    courses: list[CourseOut]
