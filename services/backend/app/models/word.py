"""课程单词 + 全局词表。

Lexicon:全局英语单词词表(spelling 唯一,小写)。课程的 words 表通过 lexeme_id 关联;
能力模型(ChildWordMastery)也按 lexeme_id 记录,实现「一个词的掌握度全局共享」。

Word:课程内单词,带 lexeme_id 指回 Lexicon。允许 NULL(迁移期间临时)。

字段：
- course_id: FK to courses.id（ON DELETE CASCADE，course 重建时 word 也清空，由 seed_words_if_empty 重新写入）
- lexeme_id: FK to lexicon.id（ON DELETE SET NULL,课程词允许脱离 lexicon 独立存在）
- spelling: 英文拼写，如 "apple"
- syllables: 音节拆分 JSON 数组，如 ["ap", "ple"]；单词 1 个则 ["school"]
- meaning_cn: 中文意思，如 "苹果"
- phonetic: IPA 音标（英式），如 "/ˈæp.əl/"；朗读练习界面展示帮助孩子记发音
- sample_sentence: 英文例句，如 "I eat an apple every day."
- sample_sentence_translation: 例句中文翻译，如 "我每天吃一个苹果。"
- sort_order: 排序（按字母序或难度）
- is_active: 软停用（admin 端可隐藏单词）
"""
from uuid import UUID, uuid4

from sqlalchemy import Boolean, ForeignKey, Index, Integer, String, Text, Uuid
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class Lexicon(Base, TimestampMixin):
    """全局英语单词词表(spelling 唯一,小写)。

    同一个 spelling 的词在多个课程(KET/PET/...)共享同一条 lexicon 记录。
    能力模型(ChildWordMastery)按 lexeme_id 聚合,实现「掌握了 500 词就是 500 词」。"""

    __tablename__ = "lexicon"
    __table_args__ = (
        Index("ux_lexicon_spelling", "spelling", unique=True),
        Index("ix_lexicon_first_letter", "first_letter"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    spelling: Mapped[str] = mapped_column(String(64), nullable=False)
    phonetic: Mapped[str | None] = mapped_column(String(128), nullable=True)
    meaning_cn: Mapped[str | None] = mapped_column(String(512), nullable=True)
    first_letter: Mapped[str] = mapped_column(String(1), nullable=False)


class Word(Base, TimestampMixin):
    __tablename__ = "words"
    __table_args__ = (
        Index("ux_words_course_spelling", "course_id", "spelling", unique=True),
        Index("ix_words_course", "course_id"),
        Index("ix_words_lexeme", "lexeme_id"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    course_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("courses.id", ondelete="CASCADE"), nullable=False
    )
    lexeme_id: Mapped[UUID | None] = mapped_column(
        Uuid, ForeignKey("lexicon.id", ondelete="SET NULL"), nullable=True
    )
    spelling: Mapped[str] = mapped_column(String(64), nullable=False)
    syllables: Mapped[list[str]] = mapped_column(JSONB, nullable=False, default=list)
    meaning_cn: Mapped[str | None] = mapped_column(Text, nullable=True)
    phonetic: Mapped[str | None] = mapped_column(Text, nullable=True)
    sample_sentence: Mapped[str | None] = mapped_column(Text, nullable=True)
    sample_sentence_translation: Mapped[str | None] = mapped_column(Text, nullable=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
