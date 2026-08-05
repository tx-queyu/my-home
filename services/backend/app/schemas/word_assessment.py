"""Pydantic 模型 —— 单词评测结果。"""
from uuid import UUID

from pydantic import BaseModel, Field


class WordAssessmentResult(BaseModel):
    """讯飞 ISE 评测返回给前端的结果。"""
    word_id: str
    ref_text: str
    score: int = Field(ge=0, le=100)
    passed: bool
    enabled: bool = True  # false 表示 ISE 未配置，前端降级处理


class WordScoreIn(BaseModel):
    """学习/测评课离线评分回写（客户端已完成判对错）。"""
    score: int = Field(ge=0, le=100)


class WordScoreOut(BaseModel):
    """离线评分回写后的 mastery 快照。"""
    word_id: UUID
    lexeme_id: UUID
    mastery: float
    attempts: int
    passed_count: int
    best_score: int
    last_score: int | None

