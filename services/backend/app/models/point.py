"""积分账户 + 流水。"""
import enum
from uuid import UUID, uuid4

from sqlalchemy import Enum as SAEnum
from sqlalchemy import ForeignKey, Index, Integer, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class PointSource(str, enum.Enum):
    task = "task"
    redemption = "redemption"
    adjustment = "adjustment"
    checkin = "checkin"


class PointAccount(Base, TimestampMixin):
    __tablename__ = "point_accounts"
    __table_args__ = (Index("ux_point_accounts_user", "user_id", unique=True),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    balance: Mapped[int] = mapped_column(Integer, default=0, nullable=False)


class PointTransaction(Base, TimestampMixin):
    __tablename__ = "point_transactions"
    __table_args__ = (Index("ix_point_transactions_user_id", "user_id"),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    delta: Mapped[int] = mapped_column(Integer, nullable=False)
    source: Mapped[PointSource] = mapped_column(
        SAEnum(PointSource, name="point_source"), nullable=False
    )
    ref_id: Mapped[UUID | None] = mapped_column(Uuid, nullable=True)
    note: Mapped[str | None] = mapped_column(String(256), nullable=True)
