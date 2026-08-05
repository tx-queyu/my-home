"""系统预置课程（非家庭级，启动时 seed）。

2 层结构：subject（学科，13 个）→ textbook（教材，如 小学人教版一年级上）→
learning_method（学习方式，7 种：朗读/背诵/听力/口语/写作/授课/测试）。

display label 由前端拼 `"{textbook} · {learning_method}"`。
"""
from uuid import UUID, uuid4

from sqlalchemy import Boolean, Index, Integer, SmallInteger, String, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class Course(Base, TimestampMixin):
    __tablename__ = "courses"
    __table_args__ = (
        Index("ux_courses_subject_textbook_method", "subject", "textbook", "learning_method", unique=True),
        Index("ix_courses_subject", "subject"),
        Index("ix_courses_textbook", "textbook"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    subject: Mapped[str] = mapped_column(String(32), nullable=False)
    textbook: Mapped[str] = mapped_column(String(64), nullable=False)
    learning_method: Mapped[str] = mapped_column(String(32), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    default_points: Mapped[int] = mapped_column(SmallInteger, default=10, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    sort_order: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
