"""设备管控：Device + DeviceCommand。"""
import enum
from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import Enum as SAEnum
from sqlalchemy import ForeignKey, Index, String, Uuid, DateTime, Boolean
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class DeviceCommandType(str, enum.Enum):
    enable_block = "enable_block"
    disable_block = "disable_block"


class DeviceCommandStatus(str, enum.Enum):
    pending = "pending"
    executing = "executing"
    succeeded = "succeeded"
    failed = "failed"


class Device(Base, TimestampMixin):
    __tablename__ = "devices"
    __table_args__ = (Index("ix_devices_family_id", "family_id"),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    device_name: Mapped[str] = mapped_column(String(128), nullable=False)
    is_device_owner: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    is_blocked: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    last_seen: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    os_type: Mapped[str] = mapped_column(String(16), nullable=False, default="android")
    os_version: Mapped[str | None] = mapped_column(String(64), nullable=True)
    manufacturer: Mapped[str | None] = mapped_column(String(64), nullable=True)
    model: Mapped[str | None] = mapped_column(String(128), nullable=True)


class DeviceCommand(Base, TimestampMixin):
    __tablename__ = "device_commands"
    __table_args__ = (Index("ix_device_commands_device_id", "device_id"),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    device_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("devices.id", ondelete="CASCADE"), nullable=False
    )
    command_type: Mapped[DeviceCommandType] = mapped_column(
        SAEnum(DeviceCommandType, name="device_command_type"),
        nullable=False,
    )
    status: Mapped[DeviceCommandStatus] = mapped_column(
        SAEnum(DeviceCommandStatus, name="device_command_status"),
        nullable=False,
        default=DeviceCommandStatus.pending,
    )
    error: Mapped[str | None] = mapped_column(String(256), nullable=True)
    executed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
