"""系统管理员路由：跨家庭用户 + 家庭管理。"""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.role_groups import ROLE_EXCLUSIVE_GROUPS
from app.core.security import hash_password, require_admin
from app.models import Family, PointAccount, User, UserRole
from app.schemas.system import (
    SystemFamilyDetailOut,
    SystemFamilyOut,
    SystemFamilyPage,
    SystemRoleOut,
    SystemUserCreateRequest,
    SystemUserOut,
    SystemUserPage,
    SystemUserUpdateRequest,
)
from app.schemas.auth import ResetPasswordByAdminRequest

router = APIRouter(prefix="/api/system", tags=["system"])

ROLE_DESCRIPTIONS = {
    "family_admin": "家庭管理员：创建者默认角色，可管理家庭成员、授权其他成员成为家庭管理员",
    "parent": "家长：管理家庭任务与奖励，可管理家庭成员",
    "child": "孩子：完成任务、累积积分、兑换奖励",
    "admin": "系统管理员：管理所有用户、家庭与角色",
}


@router.get("/users", response_model=SystemUserPage)
async def list_users(
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=100),
    family_id: str | None = Query(None, description="UUID 字符串，或特殊值 'none' 表示无家庭"),
    role: str | None = Query(None, description="parent/child/family_admin/admin"),
    active: bool | None = Query(None),
    q: str | None = Query(None, description="模糊匹配 username/display_name"),
):
    conditions = []
    if family_id:
        if family_id == "none":
            conditions.append(User.family_id.is_(None))
        else:
            conditions.append(User.family_id == UUID(family_id))
    if role:
        conditions.append(func.array_position(User.roles, UserRole(role)).isnot(None))
    if active is not None:
        conditions.append(User.is_active == active)
    if q:
        pattern = f"%{q}%"
        conditions.append(
            (User.username.ilike(pattern)) | (User.display_name.ilike(pattern))
        )

    count_stmt = select(func.count(User.id))
    if conditions:
        count_stmt = count_stmt.where(*conditions)
    total = (await db.execute(count_stmt)).scalar_one()

    data_stmt = (
        select(User, Family.name.label("family_name"))
        .outerjoin(Family, User.family_id == Family.id)
    )
    if conditions:
        data_stmt = data_stmt.where(*conditions)
    data_stmt = (
        data_stmt.order_by(User.created_at).limit(size).offset((page - 1) * size)
    )
    rows = (await db.execute(data_stmt)).all()
    items = [
        SystemUserOut(
            id=str(u.id),
            username=u.username,
            display_name=u.display_name,
            roles=[r.value for r in u.roles],
            family_id=str(u.family_id) if u.family_id else None,
            family_name=fname,
            is_active=u.is_active,
        )
        for u, fname in rows
    ]
    return SystemUserPage(items=items, total=total, page=page, size=size)


@router.post("/users", response_model=SystemUserOut, status_code=status.HTTP_201_CREATED)
async def create_user(
    payload: SystemUserCreateRequest,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    # 先查重 username，避免 autoflush IntegrityError 漏捕获
    existing = await db.execute(select(User).where(User.username == payload.username))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="username_taken")

    family_id = UUID(payload.family_id) if payload.family_id else None
    family_name: str | None = None
    if family_id:
        fam_result = await db.execute(select(Family).where(Family.id == family_id))
        family = fam_result.scalar_one_or_none()
        if not family:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="family_not_found")
        family_name = family.name

    user = User(
        family_id=family_id,
        username=payload.username,
        password_hash=hash_password(payload.password),
        display_name=payload.display_name,
        roles=[UserRole(r) for r in payload.roles],
        is_active=payload.is_active,
    )
    db.add(user)
    await db.flush()
    db.add(PointAccount(user_id=user.id, balance=0))
    await db.commit()
    await db.refresh(user)
    return SystemUserOut(
        id=str(user.id),
        username=user.username,
        display_name=user.display_name,
        roles=[r.value for r in user.roles],
        family_id=str(user.family_id) if user.family_id else None,
        family_name=family_name,
        is_active=user.is_active,
    )


@router.put("/users/{user_id}", response_model=SystemUserOut)
async def update_user(
    user_id: UUID,
    payload: SystemUserUpdateRequest,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    if user_id == admin.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="cannot_modify_self",
        )
    result = await db.execute(select(User).where(User.id == user_id))
    target = result.scalar_one_or_none()
    if not target:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="user_not_found")

    target.roles = [UserRole(r) for r in payload.roles]
    target.family_id = UUID(payload.family_id) if payload.family_id else None
    target.is_active = payload.is_active

    family_name: str | None = None
    if target.family_id:
        fam_result = await db.execute(select(Family).where(Family.id == target.family_id))
        family = fam_result.scalar_one_or_none()
        family_name = family.name if family else None

    try:
        await db.commit()
    except Exception:
        await db.rollback()
        raise
    await db.refresh(target)
    return SystemUserOut(
        id=str(target.id),
        username=target.username,
        display_name=target.display_name,
        roles=[r.value for r in target.roles],
        family_id=str(target.family_id) if target.family_id else None,
        family_name=family_name,
        is_active=target.is_active,
    )


@router.delete("/users/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_user(
    user_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    if user_id == admin.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="cannot_delete_self",
        )
    result = await db.execute(select(User).where(User.id == user_id))
    target = result.scalar_one_or_none()
    if not target:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="user_not_found")
    try:
        await db.delete(target)
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="user_in_use")
    return None


@router.post("/users/{user_id}/reset-password", status_code=status.HTTP_204_NO_CONTENT)
async def admin_reset_user_password(
    user_id: UUID,
    payload: ResetPasswordByAdminRequest,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """系统管理员重置任意用户密码。"""
    result = await db.execute(select(User).where(User.id == user_id))
    target = result.scalar_one_or_none()
    if not target:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="user_not_found")
    target.password_hash = hash_password(payload.new_password)
    await db.commit()
    return None


@router.get("/families", response_model=SystemFamilyPage)
async def list_families(
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=100),
    q: str | None = Query(None, description="模糊匹配家庭名称"),
    has_members: bool | None = Query(
        None, description="True=仅有成员的家庭，False=仅空家庭，None=全部"
    ),
):
    base = (
        select(
            Family.id,
            Family.name,
            func.count(User.id).label("member_count"),
        )
        .outerjoin(User, User.family_id == Family.id)
        .group_by(Family.id, Family.name)
    )
    if q:
        base = base.having(Family.name.ilike(f"%{q}%"))
    if has_members is True:
        base = base.having(func.count(User.id) > 0)
    elif has_members is False:
        base = base.having(func.count(User.id) == 0)

    total_stmt = select(func.count()).select_from(base.subquery())
    total = (await db.execute(total_stmt)).scalar_one()

    rows_stmt = base.order_by(Family.created_at).limit(size).offset((page - 1) * size)
    rows = (await db.execute(rows_stmt)).all()
    items = [
        SystemFamilyOut(
            id=str(fid),
            name=fname,
            member_count=cnt or 0,
        )
        for fid, fname, cnt in rows
    ]
    return SystemFamilyPage(items=items, total=total, page=page, size=size)


@router.get("/families/{family_id}", response_model=SystemFamilyDetailOut)
async def get_family_detail(
    family_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    fam_result = await db.execute(select(Family).where(Family.id == family_id))
    family = fam_result.scalar_one_or_none()
    if not family:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="family_not_found")

    members_stmt = (
        select(User, Family.name.label("family_name"))
        .outerjoin(Family, User.family_id == Family.id)
        .where(User.family_id == family.id)
        .order_by(User.created_at)
    )
    rows = (await db.execute(members_stmt)).all()
    members = [
        SystemUserOut(
            id=str(u.id),
            username=u.username,
            display_name=u.display_name,
            roles=[r.value for r in u.roles],
            family_id=str(u.family_id) if u.family_id else None,
            family_name=fname,
            is_active=u.is_active,
        )
        for u, fname in rows
    ]
    return SystemFamilyDetailOut(
        id=str(family.id),
        name=family.name,
        member_count=len(members),
        created_at=family.created_at,
        members=members,
    )


@router.delete("/families/{family_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_family(
    family_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    fam_result = await db.execute(select(Family).where(Family.id == family_id))
    family = fam_result.scalar_one_or_none()
    if not family:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="family_not_found")

    member_count = (
        await db.execute(
            select(func.count(User.id)).where(User.family_id == family.id)
        )
    ).scalar_one()
    if member_count > 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="family_not_empty",
        )

    await db.delete(family)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="family_in_use",
        )
    return None


@router.get("/roles", response_model=list[SystemRoleOut])
async def list_roles(
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    role_expr = func.unnest(User.roles).label("role")
    stmt = select(role_expr, func.count(User.id)).group_by(role_expr)
    rows = (await db.execute(stmt)).all()
    counts: dict[str, int] = {}
    for row in rows:
        counts[str(row[0])] = row[1] or 0
    return [
        SystemRoleOut(
            role=role,
            count=counts.get(role, 0),
            description=desc,
            exclusive_group=ROLE_EXCLUSIVE_GROUPS.get(role),
        )
        for role, desc in ROLE_DESCRIPTIONS.items()
    ]
