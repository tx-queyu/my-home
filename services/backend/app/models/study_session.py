"""学习时长会话（v0.17.0）—— 互动课程学习埋点。

三个互动 session（朗读/学习/测评）自然完成时自动上报，不给积分：
- 冗余 subject/textbook/learning_method 三字段而非 course_id：统计按教材分布、
  Course 可停用而历史须可读、客户端上报时手里已有 course 对象
- session_type/source 用 String + Pydantic Literal 校验（不用 DB enum，避免 ALTER TYPE）
- 个人级（无 family_id，按 user_id 隔离，同 SelfStudyTextbook 模式）；
  跨家庭防护由 API 层对 target user 做同 family 显式校验
"""
from datetime import date
from uuid import UUID, uuid4

from sqlalchemy import Date, ForeignKey, Index, Integer, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class StudySession(Base, TimestampMixin):
    __tablename__ = "study_sessions"
    __table_args__ = (
        # 主查询模式：按用户 + 日期范围聚合（今日/本周/累计）
        Index("ix_study_sessions_user_date", "user_id", "session_date"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    subject: Mapped[str] = mapped_column(String(32), nullable=False)
    textbook: Mapped[str] = mapped_column(String(64), nullable=False)
    learning_method: Mapped[str] = mapped_column(String(32), nullable=False)
    # reading | learn | quiz
    session_type: Mapped[str] = mapped_column(String(16), nullable=False)
    # task | experience | self_study
    source: Mapped[str] = mapped_column(String(16), nullable=False)
    duration_seconds: Mapped[int] = mapped_column(Integer, nullable=False)
    session_date: Mapped[date] = mapped_column(
        Date, nullable=False, server_default=func.current_date()
    )
