"""设备管控 Pydantic 模型。"""
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field

from app.models import DeviceCommandStatus, DeviceCommandType


class DeviceRegister(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    os_type: str = Field(default="android", max_length=16)
    os_version: str | None = Field(default=None, max_length=64)
    manufacturer: str | None = Field(default=None, max_length=64)
    model: str | None = Field(default=None, max_length=128)


class DeviceOut(BaseModel):
    id: UUID
    family_id: UUID
    user_id: UUID
    device_name: str
    is_device_owner: bool
    is_blocked: bool
    last_seen: datetime | None = None
    created_at: datetime
    updated_at: datetime
    username: str | None = None
    display_name: str | None = None
    family_name: str | None = None
    os_type: str = "android"
    os_version: str | None = None
    manufacturer: str | None = None
    model: str | None = None

    model_config = {"from_attributes": True}


class DeviceCommandCreate(BaseModel):
    command_type: DeviceCommandType


class DeviceCommandOut(BaseModel):
    id: UUID
    device_id: UUID
    command_type: DeviceCommandType
    status: DeviceCommandStatus
    error: str | None = None
    created_at: datetime
    executed_at: datetime | None = None

    model_config = {"from_attributes": True}


class DeviceCommandAck(BaseModel):
    success: bool
    error: str | None = Field(default=None, max_length=256)
    is_device_owner: bool
    is_blocked: bool
