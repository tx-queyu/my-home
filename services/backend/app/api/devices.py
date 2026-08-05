"""设备管控：注册、列表、详情、发命令、长轮询、ack。"""
import asyncio
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, require_parent
from app.models import Device, DeviceCommand, DeviceCommandStatus, DeviceCommandType, Family, User
from app.schemas.device import (
    DeviceCommandAck,
    DeviceCommandCreate,
    DeviceCommandOut,
    DeviceOut,
    DeviceRegister,
)

router = APIRouter(prefix="/api/devices", tags=["devices"])


def _device_to_out(
    device: Device,
    username: str | None = None,
    display_name: str | None = None,
    family_name: str | None = None,
) -> DeviceOut:
    return DeviceOut(
        id=device.id,
        family_id=device.family_id,
        user_id=device.user_id,
        device_name=device.device_name,
        is_device_owner=device.is_device_owner,
        is_blocked=device.is_blocked,
        last_seen=device.last_seen,
        created_at=device.created_at,
        updated_at=device.updated_at,
        username=username,
        display_name=display_name,
        family_name=family_name,
        os_type=device.os_type,
        os_version=device.os_version,
        manufacturer=device.manufacturer,
        model=device.model,
    )


async def _get_owned_device(
    db: AsyncSession, device_id: UUID, family_id: UUID | None
) -> Device:
    if family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="device_not_found")
    result = await db.execute(
        select(Device).where(Device.id == device_id, Device.family_id == family_id)
    )
    device = result.scalar_one_or_none()
    if not device:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="device_not_found")
    return device


@router.post("/register", response_model=DeviceOut, status_code=status.HTTP_201_CREATED)
async def register_device(
    payload: DeviceRegister,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    device = Device(
        family_id=user.family_id,
        user_id=user.id,
        device_name=payload.name,
        is_device_owner=False,
        is_blocked=False,
        last_seen=datetime.now(timezone.utc),
        os_type=payload.os_type,
        os_version=payload.os_version,
        manufacturer=payload.manufacturer,
        model=payload.model,
    )
    db.add(device)
    await db.commit()
    await db.refresh(device)
    return _device_to_out(device, user.username, user.display_name)


@router.get("", response_model=list[DeviceOut])
async def list_devices(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        return []
    result = await db.execute(
        select(Device, User.username, User.display_name, Family.name.label("family_name"))
        .outerjoin(User, User.id == Device.user_id)
        .outerjoin(Family, Family.id == Device.family_id)
        .where(Device.family_id == user.family_id)
        .order_by(Device.created_at.desc())
    )
    rows = result.all()
    return [
        _device_to_out(device, uname, dname, famname)
        for device, uname, dname, famname in rows
    ]


@router.get("/{device_id}", response_model=DeviceOut)
async def get_device(
    device_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="device_not_found")
    result = await db.execute(
        select(Device, User.username, User.display_name, Family.name.label("family_name"))
        .outerjoin(User, User.id == Device.user_id)
        .outerjoin(Family, Family.id == Device.family_id)
        .where(Device.id == device_id, Device.family_id == user.family_id)
    )
    row = result.first()
    if not row:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="device_not_found")
    device, uname, dname, famname = row
    return _device_to_out(device, uname, dname, famname)


@router.post(
    "/{device_id}/commands",
    response_model=DeviceCommandOut,
    status_code=status.HTTP_201_CREATED,
)
async def issue_command(
    device_id: UUID,
    payload: DeviceCommandCreate,
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    device = await _get_owned_device(db, device_id, user.family_id)
    cmd = DeviceCommand(
        device_id=device.id,
        command_type=payload.command_type,
        status=DeviceCommandStatus.pending,
    )
    db.add(cmd)
    await db.commit()
    await db.refresh(cmd)
    return cmd


@router.get("/me/commands/poll", response_model=list[DeviceCommandOut])
async def poll_commands(
    timeout: int = 60,
    x_device_id: UUID = Header(alias="X-Device-Id"),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    device = await _get_owned_device(db, x_device_id, user.family_id)

    deadline = asyncio.get_event_loop().time() + max(1, min(timeout, 120))
    while True:
        result = await db.execute(
            select(DeviceCommand)
            .where(
                DeviceCommand.device_id == device.id,
                DeviceCommand.status == DeviceCommandStatus.pending,
            )
            .order_by(DeviceCommand.created_at.asc())
            .limit(10)
        )
        commands = list(result.scalars().all())
        if commands:
            # 标记为 executing，避免被并发拉取重复执行
            cmd_ids = [c.id for c in commands]
            await db.execute(
                update(DeviceCommand)
                .where(DeviceCommand.id.in_(cmd_ids))
                .values(status=DeviceCommandStatus.executing)
            )
            await db.commit()
            return commands

        if asyncio.get_event_loop().time() >= deadline:
            return []
        await asyncio.sleep(1)


@router.post("/me/commands/{cmd_id}/ack", response_model=DeviceCommandOut)
async def ack_command(
    cmd_id: UUID,
    payload: DeviceCommandAck,
    x_device_id: UUID = Header(alias="X-Device-Id"),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    device = await _get_owned_device(db, x_device_id, user.family_id)
    result = await db.execute(
        select(DeviceCommand).where(
            DeviceCommand.id == cmd_id, DeviceCommand.device_id == device.id
        )
    )
    cmd = result.scalar_one_or_none()
    if not cmd:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="command_not_found")

    cmd.status = DeviceCommandStatus.succeeded if payload.success else DeviceCommandStatus.failed
    cmd.error = payload.error
    cmd.executed_at = datetime.now(timezone.utc)

    device.is_device_owner = payload.is_device_owner
    device.is_blocked = payload.is_blocked
    device.last_seen = datetime.now(timezone.utc)

    await db.commit()
    await db.refresh(cmd)
    return cmd
