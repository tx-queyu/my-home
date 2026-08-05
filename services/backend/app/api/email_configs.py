"""邮件服务商配置 — admin CRUD + activate/deactivate + test 探活。"""
import asyncio
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.crypto import decrypt_credential, encrypt_credential
from app.core.database import get_db
from app.core.security import require_admin
from app.models import EmailConfig, User
from app.schemas.verification import (
    EmailConfigCreate,
    EmailConfigResponse,
    EmailConfigUpdate,
    TestResult,
)
from app.services.email_providers import get_probe

router = APIRouter(prefix="/api/system/email-configs", tags=["system-email"])


def _to_response(cfg: EmailConfig) -> EmailConfigResponse:
    return EmailConfigResponse(
        id=cfg.id,
        provider=cfg.provider,
        is_active=cfg.is_active,
        smtp_host=cfg.smtp_host,
        smtp_port=cfg.smtp_port,
        encryption=cfg.encryption,
        username=cfg.username,
        region=cfg.region,
        from_email=cfg.from_email,
        from_name=cfg.from_name,
        access_key_id_configured=bool(cfg.access_key_id_encrypted),
        password_configured=bool(cfg.password_encrypted),
        daily_limit=cfg.daily_limit,
        interval_seconds=cfg.interval_seconds,
        created_at=cfg.created_at.isoformat() if cfg.created_at else "",
    )


def _decrypt_secrets(cfg: EmailConfig) -> dict:
    """解密 AK/SK 或 SMTP 密码给 provider 模块使用。"""
    secrets: dict = {
        "access_key_id": "",
        "access_key_secret": "",
        "password": "",
    }
    if cfg.access_key_id_encrypted:
        try:
            secrets["access_key_id"] = decrypt_credential(cfg.access_key_id_encrypted)
        except ValueError:
            pass
    if cfg.access_key_secret_encrypted:
        try:
            secrets["access_key_secret"] = decrypt_credential(cfg.access_key_secret_encrypted)
        except ValueError:
            pass
    if cfg.password_encrypted:
        try:
            secrets["password"] = decrypt_credential(cfg.password_encrypted)
        except ValueError:
            pass
    return secrets


@router.get("", response_model=list[EmailConfigResponse])
async def list_email_configs(
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(EmailConfig).order_by(EmailConfig.created_at.desc())
    rows = (await db.execute(stmt)).scalars().all()
    return [_to_response(c) for c in rows]


@router.post("", response_model=EmailConfigResponse, status_code=status.HTTP_201_CREATED)
async def create_email_config(
    payload: EmailConfigCreate,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    cfg = EmailConfig(
        provider=payload.provider,
        smtp_host=payload.smtp_host,
        smtp_port=payload.smtp_port,
        encryption=payload.encryption,
        username=payload.username,
        password_encrypted=encrypt_credential(payload.password) if payload.password else None,
        access_key_id_encrypted=encrypt_credential(payload.access_key_id) if payload.access_key_id else None,
        access_key_secret_encrypted=encrypt_credential(payload.access_key_secret) if payload.access_key_secret else None,
        region=payload.region,
        from_email=payload.from_email,
        from_name=payload.from_name,
        daily_limit=payload.daily_limit,
        interval_seconds=payload.interval_seconds,
        is_active=False,
        created_by=admin.id,
    )
    db.add(cfg)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="provider_already_exists")
    await db.refresh(cfg)
    return _to_response(cfg)


@router.put("/{cfg_id}", response_model=EmailConfigResponse)
async def update_email_config(
    cfg_id: UUID,
    payload: EmailConfigUpdate,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(EmailConfig).where(EmailConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")

    if payload.smtp_host is not None:
        cfg.smtp_host = payload.smtp_host
    if payload.smtp_port is not None:
        cfg.smtp_port = payload.smtp_port
    if payload.encryption is not None:
        cfg.encryption = payload.encryption
    if payload.username is not None:
        cfg.username = payload.username
    if payload.region is not None:
        cfg.region = payload.region
    if payload.from_email is not None:
        cfg.from_email = payload.from_email
    if payload.from_name is not None:
        cfg.from_name = payload.from_name
    if payload.daily_limit is not None:
        cfg.daily_limit = payload.daily_limit
    if payload.interval_seconds is not None:
        cfg.interval_seconds = payload.interval_seconds
    if payload.password:
        cfg.password_encrypted = encrypt_credential(payload.password)
    if payload.access_key_id:
        cfg.access_key_id_encrypted = encrypt_credential(payload.access_key_id)
    if payload.access_key_secret:
        cfg.access_key_secret_encrypted = encrypt_credential(payload.access_key_secret)

    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise
    await db.refresh(cfg)
    return _to_response(cfg)


@router.delete("/{cfg_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_email_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(EmailConfig).where(EmailConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")
    if cfg.is_active:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="cannot_delete_active_config")
    await db.delete(cfg)
    await db.commit()


@router.post("/{cfg_id}/activate", response_model=EmailConfigResponse)
async def activate_email_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(EmailConfig).where(EmailConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")

    await db.execute(update(EmailConfig).values(is_active=False))
    cfg.is_active = True
    await db.commit()
    await db.refresh(cfg)
    return _to_response(cfg)


@router.post("/{cfg_id}/deactivate", response_model=EmailConfigResponse)
async def deactivate_email_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(EmailConfig).where(EmailConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")
    cfg.is_active = False
    await db.commit()
    await db.refresh(cfg)
    return _to_response(cfg)


@router.post("/{cfg_id}/test", response_model=TestResult)
async def test_email_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(EmailConfig).where(EmailConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")

    probe_fn = get_probe(cfg.provider)
    if not probe_fn:
        return TestResult(ok=False, error=f"unknown_provider:{cfg.provider}")

    secrets = _decrypt_secrets(cfg)
    try:
        await asyncio.to_thread(probe_fn, cfg, secrets)
        return TestResult(ok=True)
    except Exception as e:
        return TestResult(ok=False, error=str(e)[:200])
