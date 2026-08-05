"""能力模型 Pydantic 模型 —— 单词维度（v0.14.0 全局化，v0.16.2 教材维度）。

v0.14.0 起从「per-course」切换到「per-lexeme」:
- SkillOverviewOut:全局能力概览(跨所有课程)
- ChildWordMasteryOut:lexeme 级别的 mastery 明细

v0.16.2 起覆盖度从「per-course」切换到「per-textbook」:
- TextbookCoverageOut:教材 = (subject, textbook);课程(朗读/学习/测评)
  只是教材的学习手段,共享同一批 lexeme,教材维度天然去重。

未来扩展词组/语法维度时复制本结构 + 加前缀（如 phrase_total_words），
不修改本 schema,避免老端点破坏。
"""
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class SkillOverviewOut(BaseModel):
    """全局能力概览 —— 能力中心首屏大卡。

    双指标:
    - coverage: 接触过(assessed)/总数
    - mastered_coverage: 已掌握(mastered)/总数
    """
    total_words: int = Field(ge=0)          # 全局 active lexeme 数
    assessed_words: int = Field(ge=0)       # 有 mastery 记录的 lexeme 数
    mastered_words: int = Field(ge=0)       # mastery >= 0.9 的 lexeme 数
    average_mastery: float = Field(ge=0.0, le=1.0)  # 已评估词的均值
    coverage: float = Field(ge=0.0, le=1.0)          # assessed / total
    mastered_coverage: float = Field(ge=0.0, le=1.0) # mastered / total
    by_state: dict[str, int]                # {"new": N, "learning": N, "familiar": N, "mastered": N}


class TextbookCoverageOut(BaseModel):
    """教材覆盖度 —— 能力中心「教材进度」卡每行(v0.16.2 起按教材聚合)。

    教材下各课程(朗读/学习/测评)共享同一批 lexeme,
    total_words 是教材全部 active 课程的 distinct lexeme 数。"""
    subject: str
    textbook: str
    learning_methods: list[str]          # 该教材下 active 课程的学习方式(按 sort_order)
    total_words: int = Field(ge=0)       # 教材全部 active 课程的 distinct lexeme 数
    touched_words: int = Field(ge=0)     # 接触过的词数
    mastered_words: int = Field(ge=0)    # 已掌握的词数
    touched_coverage: float = Field(ge=0.0, le=1.0)
    mastered_coverage: float = Field(ge=0.0, le=1.0)
    is_completed: bool                   # mastered == total && total > 0 → 通关徽标


class ChildWordMasteryOut(BaseModel):
    """孩子对某 lexeme 的掌握明细 —— 能力中心单词列表。"""
    lexeme_id: UUID
    spelling: str                            # 从 lexicon join
    meaning_cn: str | None = None
    phonetic: str | None = None
    mastery: float = Field(ge=0.0, le=1.0)
    attempts: int = Field(ge=0)
    passed_count: int = Field(ge=0)
    best_score: int = Field(ge=0, le=100)
    last_score: int | None = Field(default=None, ge=0, le=100)
    last_assessed_at: datetime | None = None
    state: str  # new | learning | familiar | mastered
