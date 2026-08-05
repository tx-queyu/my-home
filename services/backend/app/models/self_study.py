"""家长自学教材（v0.16.1）—— 用户级自选教材清单。

家长自学按「教材 → 课程（朗读/学习/测评）」两层组织:
- 教材 = (subject, textbook)，如 英语·KET、英语·托业
- 用户从系统 active 课程中挑选教材加入自己的清单（本表）
- 教材下的互动课程从 courses 表实时查（不在本表冗余）

个人清单，无 family_id——按 user_id 隔离。
"""
from uuid import UUID, uuid4

from sqlalchemy import ForeignKey, Index, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class SelfStudyTextbook(Base, TimestampMixin):
    __tablename__ = "self_study_textbooks"
    __table_args__ = (
        Index("ux_self_study_textbooks_user", "user_id", "subject", "textbook", unique=True),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    subject: Mapped[str] = mapped_column(String(32), nullable=False)
    textbook: Mapped[str] = mapped_column(String(64), nullable=False)
