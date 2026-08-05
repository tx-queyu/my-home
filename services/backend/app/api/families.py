"""家庭路由：当前家庭信息、成员管理（parent / family_admin）。"""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, hash_password, require_family_admin, require_parent
from app.models import Family, PointAccount, User, UserRole
from app.schemas.family import (
    CreateMemberRequest,
    FamilyInfo,
    MemberInfo,
    UpdateMemberRolesRequest,
)
from app.schemas.auth import ResetPasswordByAdminRequest

router = APIRouter(prefix="/api/families", tags=["families"])


@router.get("/me", response_model=FamilyInfo)
async def get_my_family(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    if user.family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="family_not_found")
    result = await db.execute(select(Family).where(Family.id == user.family_id))
    family = result.scalar_one_or_none()
    if not family:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="family_not_found")
    return FamilyInfo(id=str(family.id), name=family.name)


@router.get("/members", response_model=list[MemberInfo])
async def list_members(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.family_id is None:
        return []
    result = await db.execute(
        select(User).where(User.family_id == user.family_id).order_by(User.created_at)
    )
    return [
        MemberInfo(
            id=str(m.id),
            username=m.username,
            display_name=m.display_name,
            roles=[r.value for r in m.roles],
            is_active=m.is_active,
        )
        for m in result.scalars().all()
    ]


@router.post("/members", response_model=MemberInfo)
async def create_member(
    data: CreateMemberRequest,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    role = UserRole.parent if data.role == "parent" else UserRole.child
    member = User(
        family_id=parent.family_id,
        username=data.username,
        password_hash=hash_password(data.password),
        display_name=data.display_name,
        roles=[role],
        is_active=True,
    )
    db.add(member)
    await db.flush()
    db.add(PointAccount(user_id=member.id, balance=0))
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="username_taken")
    await db.refresh(member)
    return MemberInfo(
        id=str(member.id),
        username=member.username,
        display_name=member.display_name,
        roles=[r.value for r in member.roles],
        is_active=member.is_active,
    )


@router.delete("/members/{member_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_member(
    member_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(User).where(User.id == member_id, User.family_id == parent.family_id)
    )
    member = result.scalar_one_or_none()
    if not member:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="member_not_found")
    if member.id == parent.id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="cannot_delete_self")
    await db.delete(member)
    await db.commit()


@router.put("/members/{member_id}/roles", response_model=MemberInfo)
async def update_member_roles(
    member_id: UUID,
    payload: UpdateMemberRolesRequest,
    actor: User = Depends(require_family_admin),
    db: AsyncSession = Depends(get_db),
):
    """家庭管理员授权/收回其他成员的角色。"""
    if member_id == actor.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="cannot_modify_self_role",
        )

    result = await db.execute(
        select(User).where(User.id == member_id, User.family_id == actor.family_id)
    )
    member = result.scalar_one_or_none()
    if not member:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="member_not_found")

    new_roles = [UserRole(r) for r in payload.roles]

    # 最后一个家庭管理员不能被降级，否则家庭将无人可管理
    is_demoting_family_admin = (
        UserRole.family_admin in member.roles
        and UserRole.family_admin not in new_roles
    )
    if is_demoting_family_admin:
        cnt_result = await db.execute(
            select(func.count(User.id)).where(
                User.family_id == actor.family_id,
                User.roles.any(UserRole.family_admin),
                User.is_active.is_(True),
            )
        )
        family_admin_count = cnt_result.scalar_one()
        if family_admin_count <= 1:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="cannot_demote_last_family_admin",
            )

    member.roles = new_roles
    try:
        await db.commit()
    except Exception:
        await db.rollback()
        raise
    await db.refresh(member)
    return MemberInfo(
        id=str(member.id),
        username=member.username,
        display_name=member.display_name,
        roles=[r.value for r in member.roles],
        is_active=member.is_active,
    )


@router.post("/members/{member_id}/reset-password", status_code=status.HTTP_204_NO_CONTENT)
async def reset_member_password(
    member_id: UUID,
    payload: ResetPasswordByAdminRequest,
    admin: User = Depends(require_family_admin),
    db: AsyncSession = Depends(get_db),
):
    """家庭管理员/家长重置家庭成员密码（不需要旧密码）。"""
    if admin.family_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="no_family")
    if member_id == admin.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="use_change_password_endpoint",
        )
    result = await db.execute(
        select(User).where(User.id == member_id, User.family_id == admin.family_id)
    )
    member = result.scalar_one_or_none()
    if not member:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="member_not_found")
    member.password_hash = hash_password(payload.new_password)
    await db.commit()
