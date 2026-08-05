"""孩子能力模型 —— 单词维度（v0.14.0 全局化）。

每条记录代表「某个孩子对某个 lexeme(全局词)的掌握度」,通过 ISE 评分调用 upsert_mastery 更新。
v0.14.0 起从 word_id 改为 lexeme_id——同一个词在多个课程共享一条 mastery 记录。
未来扩展 ChildPhraseMastery / ChildGrammarMastery 时,复制本结构 + 改 FK。
"""
from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import DateTime, ForeignKey, Index, Numeric, SmallInteger, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class ChildWordMastery(Base, TimestampMixin):
    __tablename__ = "child_word_mastery"
    __table_args__ = (
        Index("ux_child_word_mastery_user_lexeme", "user_id", "lexeme_id", unique=True),
        Index("ix_child_word_mastery_user", "user_id"),
        Index("ix_child_word_mastery_family", "family_id"),
        Index("ix_child_word_mastery_lexeme", "lexeme_id"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    lexeme_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("lexicon.id", ondelete="CASCADE"), nullable=False
    )
    family_id: Mapped[UUID | None] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=True
    )

    # 0.00-1.00 连续值;UI 分级:< 0.3 learning / 0.3-0.7 learning / 0.7-0.9 familiar / >= 0.9 mastered
    mastery: Mapped[float] = mapped_column(Numeric(5, 2), default=0, nullable=False)
    attempts: Mapped[int] = mapped_column(SmallInteger, default=0, nullable=False)
    passed_count: Mapped[int] = mapped_column(SmallInteger, default=0, nullable=False)
    best_score: Mapped[int] = mapped_column(SmallInteger, default=0, nullable=False)
    last_score: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    last_assessed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
