"""家电。"""
import enum
from uuid import UUID, uuid4

from sqlalchemy import Enum as SAEnum
from sqlalchemy import ForeignKey, Index, String, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class ApplianceStatus(str, enum.Enum):
    normal = "normal"
    broken = "broken"
    in_repair = "in_repair"
    retired = "retired"


class Appliance(Base, TimestampMixin):
    __tablename__ = "appliances"
    __table_args__ = (Index("ix_appliances_family_id", "family_id"),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    type: Mapped[str] = mapped_column(String(32), nullable=False)
    location: Mapped[str] = mapped_column(String(64), nullable=False)
    status: Mapped[ApplianceStatus] = mapped_column(
        SAEnum(ApplianceStatus, name="appliance_status"),
        nullable=False,
        default=ApplianceStatus.normal,
    )
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
