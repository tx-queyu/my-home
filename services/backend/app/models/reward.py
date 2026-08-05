"""奖励 + 兑换记录。"""
import enum
from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import Boolean, DateTime, Enum as SAEnum
from sqlalchemy import ForeignKey, Index, Integer, String, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class RedemptionStatus(str, enum.Enum):
    pending = "pending"
    fulfilled = "fulfilled"
    rejected = "rejected"


class Reward(Base, TimestampMixin):
    __tablename__ = "rewards"
    __table_args__ = (Index("ix_rewards_family_id", "family_id"),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    cost: Mapped[int] = mapped_column(Integer, nullable=False)
    stock: Mapped[int | None] = mapped_column(Integer, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)


class Redemption(Base, TimestampMixin):
    __tablename__ = "redemptions"
    __table_args__ = (
        Index("ix_redemptions_family_id", "family_id"),
        Index("ix_redemptions_user_id", "user_id"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    reward_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("rewards.id", ondelete="CASCADE"), nullable=False
    )
    cost: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[RedemptionStatus] = mapped_column(
        SAEnum(RedemptionStatus, name="redemption_status"),
        nullable=False,
        default=RedemptionStatus.pending,
    )
    handled_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    handled_by: Mapped[UUID | None] = mapped_column(Uuid, nullable=True)
