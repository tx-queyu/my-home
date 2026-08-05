"""认证路由：注册、登录、刷新、当前用户、验证码、改绑。"""
import asyncio

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.crypto import decrypt_credential
from app.core.database import get_db
from app.core.security import (
    create_access_token,
    create_refresh_token,
    decode_token,
    get_current_user,
    hash_password,
    verify_password,
)
from app.models import Family, PointAccount, User, UserRole
from app.models.verification import EmailConfig, SmsConfig
from app.schemas.auth import (
    AuthResponse,
    ChangePasswordRequest,
    LoginRequest,
    RegisterRequest,
    TokenRefreshRequest,
    UserInfo,
)
from app.schemas.verification import (
    ChangeEmailRequest,
    ChangePhoneRequest,
    LoginByCodeRequest,
    ResetPasswordRequest,
    VerificationCodeSendRequest,
    VerificationCodeVerifyRequest,
    VerifyTokenResponse,
)
from app.services.email_providers import get_sender as get_email_sender
from app.services.sms_providers import get_sender as get_sms_sender
from app.services.verification_code_service import (
    check_rate_limit,
    decode_verify_token,
    issue_code,
    invalidate_target_codes,
    verify_and_consume,
)

router = APIRouter(prefix="/api/auth", tags=["auth"])


def _user_to_info(user: User) -> UserInfo:
    return UserInfo(
        id=str(user.id),
        username=user.username,
        display_name=user.display_name,
        roles=[r.value for r in user.roles],
        family_id=str(user.family_id) if user.family_id else None,
        phone=user.phone,
        email=user.email,
        phone_verified=user.phone_verified,
        email_verified=user.email_verified,
    )


@router.post("/register", response_model=AuthResponse)
async def register(data: RegisterRequest, db: AsyncSession = Depends(get_db)):
    if not settings.registration_enabled:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="registration_disabled")

    phone: str | None = None
    email: str | None = None
    phone_verified = False
    email_verified = False

    if data.verify_token:
        vp = decode_verify_token(data.verify_token)
        if not vp or vp.get("purpose") != "register":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="verify_token_invalid")
        channel = vp.get("channel")
        target = vp.get("target")
        if channel == "sms":
            if not data.phone or data.phone != target:
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="phone_mismatch")
            phone = data.phone
            phone_verified = True
        elif channel == "email":
            if not data.email or data.email != target:
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="email_mismatch")
            email = data.email
            email_verified = True
        else:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="verify_token_invalid")

    family = Family(name=data.family_name)
    db.add(family)
    await db.flush()

    user = User(
        family_id=family.id,
        username=data.username,
        password_hash=hash_password(data.password),
        display_name=data.display_name,
        roles=[UserRole.family_admin, UserRole.parent],
        is_active=True,
        phone=phone,
        email=email,
        phone_verified=phone_verified,
        email_verified=email_verified,
    )
    db.add(user)
    await db.flush()
    db.add(PointAccount(user_id=user.id, balance=0))
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        if phone:
            existing = await db.execute(
                select(User).where(User.phone == phone, User.phone_verified == True)  # noqa: E712
            )
            if existing.scalar_one_or_none():
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="phone_in_use")
        if email:
            existing = await db.execute(
                select(User).where(User.email == email, User.email_verified == True)  # noqa: E712
            )
            if existing.scalar_one_or_none():
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="email_in_use")
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="username_taken")
    await db.refresh(user)

    access = create_access_token(user.id, [r.value for r in user.roles], user.family_id)
    refresh = create_refresh_token(user.id)
    return AuthResponse(
        access_token=access,
        refresh_token=refresh,
        user=_user_to_info(user),
    )


@router.post("/login", response_model=AuthResponse)
async def login(data: LoginRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(User).where(User.username == data.username))
    user = result.scalar_one_or_none()
    if not user or not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid_credentials")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="user_disabled")

    access = create_access_token(user.id, [r.value for r in user.roles], user.family_id)
    refresh = create_refresh_token(user.id)
    return AuthResponse(
        access_token=access,
        refresh_token=refresh,
        user=_user_to_info(user),
    )


@router.post("/refresh", response_model=AuthResponse)
async def refresh(data: TokenRefreshRequest, db: AsyncSession = Depends(get_db)):
    payload = decode_token(data.refresh_token)
    if payload.get("type") != "refresh":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid_token_type")

    result = await db.execute(select(User).where(User.id == payload["sub"]))
    user = result.scalar_one_or_none()
    if not user or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="user_not_found_or_inactive")

    access = create_access_token(user.id, [r.value for r in user.roles], user.family_id)
    new_refresh = create_refresh_token(user.id)
    return AuthResponse(
        access_token=access,
        refresh_token=new_refresh,
        user=_user_to_info(user),
    )


@router.get("/me", response_model=UserInfo)
async def get_me(user: User = Depends(get_current_user)):
    return _user_to_info(user)


# ---- 验证码 ----

async def _get_active_provider(db: AsyncSession, channel: str):
    """返回 (cfg, secrets, sender_fn) 或 None。"""
    if channel == "sms":
        result = await db.execute(select(SmsConfig).where(SmsConfig.is_active == True))  # noqa: E712
        cfg = result.scalar_one_or_none()
        if not cfg:
            return None
        sender = get_sms_sender(cfg.provider)
        if not sender:
            return None
        secrets = {
            "access_key_id": decrypt_credential(cfg.access_key_id_encrypted) if cfg.access_key_id_encrypted else "",
            "access_key_secret": decrypt_credential(cfg.access_key_secret_encrypted) if cfg.access_key_secret_encrypted else "",
        }
        return cfg, secrets, sender
    if channel == "email":
        result = await db.execute(select(EmailConfig).where(EmailConfig.is_active == True))  # noqa: E712
        cfg = result.scalar_one_or_none()
        if not cfg:
            return None
        sender = get_email_sender(cfg.provider)
        if not sender:
            return None
        secrets = {
            "access_key_id": decrypt_credential(cfg.access_key_id_encrypted) if cfg.access_key_id_encrypted else "",
            "access_key_secret": decrypt_credential(cfg.access_key_secret_encrypted) if cfg.access_key_secret_encrypted else "",
            "password": decrypt_credential(cfg.password_encrypted) if cfg.password_encrypted else "",
        }
        return cfg, secrets, sender
    return None


@router.post("/verification-code/send")
async def send_verification_code(
    payload: VerificationCodeSendRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    ip = request.client.host if request.client else None
    allowed = await check_rate_limit(db, target=payload.target, ip=ip)
    if not allowed:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="rate_limited")

    provider_info = await _get_active_provider(db, payload.channel)
    if not provider_info:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="no_active_provider")

    cfg, secrets, sender = provider_info

    code = await issue_code(
        db,
        channel=payload.channel,
        target=payload.target,
        purpose=payload.purpose,
        ip=ip,
    )

    try:
        await asyncio.to_thread(sender, cfg, secrets, payload.target, code)
    except Exception:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="code_send_failed",
        )

    await db.commit()
    return {"sent": True}


@router.post("/verification-code/verify", response_model=VerifyTokenResponse)
async def verify_code(
    payload: VerificationCodeVerifyRequest,
    db: AsyncSession = Depends(get_db),
):
    verify_token = await verify_and_consume(
        db,
        channel=payload.channel,
        target=payload.target,
        purpose=payload.purpose,
        code=payload.code,
    )
    if not verify_token:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="invalid_code")
    return VerifyTokenResponse(
        verify_token=verify_token,
        expires_in=settings.verify_token_ttl_minutes * 60,
    )


@router.post("/login-by-code", response_model=AuthResponse)
async def login_by_code(
    payload: LoginByCodeRequest,
    db: AsyncSession = Depends(get_db),
):
    vp = decode_verify_token(payload.verify_token)
    if not vp or vp.get("purpose") != "login_by_code":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="verify_token_invalid")

    target = vp.get("target")
    channel = vp.get("channel")

    if channel == "sms":
        result = await db.execute(
            select(User).where(User.phone == target, User.phone_verified == True)  # noqa: E712
        )
    elif channel == "email":
        result = await db.execute(
            select(User).where(User.email == target, User.email_verified == True)  # noqa: E712
        )
    else:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="verify_token_invalid")

    user = result.scalar_one_or_none()
    if not user or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="user_not_found_or_inactive")

    access = create_access_token(user.id, [r.value for r in user.roles], user.family_id)
    refresh = create_refresh_token(user.id)
    return AuthResponse(
        access_token=access,
        refresh_token=refresh,
        user=_user_to_info(user),
    )


@router.post("/reset-password", status_code=status.HTTP_204_NO_CONTENT)
async def reset_password(
    payload: ResetPasswordRequest,
    db: AsyncSession = Depends(get_db),
):
    vp = decode_verify_token(payload.verify_token)
    if not vp or vp.get("purpose") != "reset_password":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="verify_token_invalid")

    target = vp.get("target")
    channel = vp.get("channel")

    if channel == "sms":
        result = await db.execute(
            select(User).where(User.phone == target, User.phone_verified == True)  # noqa: E712
        )
    elif channel == "email":
        result = await db.execute(
            select(User).where(User.email == target, User.email_verified == True)  # noqa: E712
        )
    else:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="verify_token_invalid")

    user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="user_not_found")

    user.password_hash = hash_password(payload.new_password)
    await db.commit()


@router.post("/change-password", status_code=status.HTTP_204_NO_CONTENT)
async def change_password(
    payload: ChangePasswordRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if not verify_password(payload.current_password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="invalid_current_password")
    user.password_hash = hash_password(payload.new_password)
    await db.commit()


@router.post("/change-phone", response_model=UserInfo)
async def change_phone(
    payload: ChangePhoneRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    vp = decode_verify_token(payload.verify_token)
    if not vp or vp.get("purpose") != "change_phone" or vp.get("channel") != "sms":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="verify_token_invalid")
    if vp.get("target") != payload.new_phone:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="phone_mismatch")

    existing = await db.execute(
        select(User).where(User.phone == payload.new_phone, User.phone_verified == True)  # noqa: E712
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="phone_in_use")

    old_phone = user.phone
    user.phone = payload.new_phone
    user.phone_verified = True
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="phone_in_use")

    if old_phone:
        await invalidate_target_codes(db, old_phone)
        await db.commit()

    await db.refresh(user)
    return _user_to_info(user)


@router.post("/change-email", response_model=UserInfo)
async def change_email(
    payload: ChangeEmailRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    vp = decode_verify_token(payload.verify_token)
    if not vp or vp.get("purpose") != "change_email" or vp.get("channel") != "email":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="verify_token_invalid")
    if vp.get("target") != payload.new_email:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="email_mismatch")

    existing = await db.execute(
        select(User).where(User.email == payload.new_email, User.email_verified == True)  # noqa: E712
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="email_in_use")

    old_email = user.email
    user.email = payload.new_email
    user.email_verified = True
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="email_in_use")

    if old_email:
        await invalidate_target_codes(db, old_email)
        await db.commit()

    await db.refresh(user)
    return _user_to_info(user)
