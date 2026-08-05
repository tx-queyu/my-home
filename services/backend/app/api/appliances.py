"""家电 CRUD：全部按当前用户 family_id 过滤。"""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user
from app.models import Appliance, User
from app.schemas.appliance import ApplianceCreate, ApplianceOut, ApplianceUpdate

router = APIRouter(prefix="/api/appliances", tags=["appliances"])


async def _get_owned(db: AsyncSession, appliance_id: UUID, family_id: UUID) -> Appliance:
    result = await db.execute(
        select(Appliance).where(Appliance.id == appliance_id, Appliance.family_id == family_id)
    )
    appliance = result.scalar_one_or_none()
    if not appliance:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="appliance_not_found")
    return appliance


@router.get("", response_model=list[ApplianceOut])
async def list_appliances(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        return []
    result = await db.execute(
        select(Appliance)
        .where(Appliance.family_id == user.family_id)
        .order_by(Appliance.created_at.desc())
    )
    return result.scalars().all()


@router.post("", response_model=ApplianceOut, status_code=status.HTTP_201_CREATED)
async def create_appliance(
    data: ApplianceCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    appliance = Appliance(
        family_id=user.family_id,
        name=data.name,
        type=data.type,
        location=data.location,
        status=data.status,
        notes=data.notes,
    )
    db.add(appliance)
    await db.commit()
    await db.refresh(appliance)
    return appliance


@router.get("/{appliance_id}", response_model=ApplianceOut)
async def get_appliance(
    appliance_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await _get_owned(db, appliance_id, user.family_id)


@router.put("/{appliance_id}", response_model=ApplianceOut)
async def update_appliance(
    appliance_id: UUID,
    data: ApplianceUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    appliance = await _get_owned(db, appliance_id, user.family_id)
    appliance.name = data.name
    appliance.type = data.type
    appliance.location = data.location
    appliance.status = data.status
    appliance.notes = data.notes
    await db.commit()
    await db.refresh(appliance)
    return appliance


@router.delete("/{appliance_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_appliance(
    appliance_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    appliance = await _get_owned(db, appliance_id, user.family_id)
    await db.delete(appliance)
    await db.commit()
