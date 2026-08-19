"""学科成绩（v0.17.0）—— 家长录入的单条考试成绩。

- 家庭级（family_id 隔离），单条录入：学科 + 分数/满分 + 考试名 + 日期 + 备注
- subject 用自由文本（对齐 Course.subject 取值习惯，不做 FK——Course 是
  (subject, textbook, learning_method) 三元组无纯学科行，成绩学科只是概念标签）
- assignee_user_id = 这条成绩属于哪个家庭成员（孩子）
"""
from datetime import date
from uuid import UUID, uuid4

from sqlalchemy import Date, Float, ForeignKey, Index, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin


class Grade(Base, TimestampMixin):
    __tablename__ = "grades"
    __table_args__ = (
        Index("ix_grades_family_id", "family_id"),
        Index("ix_grades_assignee", "assignee_user_id"),
        Index("ix_grades_family_subject", "family_id", "subject"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=False
    )
    subject: Mapped[str] = mapped_column(String(32), nullable=False)
    score: Mapped[float] = mapped_column(Float, nullable=False)
    score_full: Mapped[float] = mapped_column(Float, nullable=False, default=100.0)
    exam_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    exam_date: Mapped[date] = mapped_column(Date, nullable=False)
    note: Mapped[str | None] = mapped_column(String(256), nullable=True)
    # 这条成绩属于哪个成员（NOT NULL）
    assignee_user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    # 录入家长（可空：录入者被删时成绩保留）
    created_by: Mapped[UUID | None] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )

    assignee = relationship("User", lazy="joined", foreign_keys=[assignee_user_id])

    @property
    def assignee_username(self) -> str | None:
        return self.assignee.username if self.assignee is not None else None
