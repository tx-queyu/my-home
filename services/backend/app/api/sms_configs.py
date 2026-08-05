"""短信服务商配置 — admin CRUD + activate/deactivate + test 探活。"""
import asyncio
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.crypto import decrypt_credential, encrypt_credential
from app.core.database import get_db
from app.core.security import require_admin
from app.models import SmsConfig, User
from app.schemas.verification import (
    SmsConfigCreate,
    SmsConfigResponse,
    SmsConfigUpdate,
    TestResult,
)
from app.services.sms_providers import get_probe

router = APIRouter(prefix="/api/system/sms-configs", tags=["system-sms"])


def _to_response(cfg: SmsConfig) -> SmsConfigResponse:
    return SmsConfigResponse(
        id=cfg.id,
        provider=cfg.provider,
        is_active=cfg.is_active,
        sign_name=cfg.sign_name,
        template_code=cfg.template_code,
        sdk_app_id=cfg.sdk_app_id,
        region=cfg.region,
        access_key_id_configured=bool(cfg.access_key_id_encrypted),
        daily_limit=cfg.daily_limit,
        interval_seconds=cfg.interval_seconds,
        created_at=cfg.created_at.isoformat() if cfg.created_at else "",
    )


def _decrypt_secrets(cfg: SmsConfig) -> dict:
    """解密 AK/SK 给 provider 模块使用。"""
    secrets: dict = {
        "access_key_id": "",
        "access_key_secret": "",
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
    return secrets


@router.get("", response_model=list[SmsConfigResponse])
async def list_sms_configs(
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(SmsConfig).order_by(SmsConfig.created_at.desc())
    rows = (await db.execute(stmt)).scalars().all()
    return [_to_response(c) for c in rows]


@router.post("", response_model=SmsConfigResponse, status_code=status.HTTP_201_CREATED)
async def create_sms_config(
    payload: SmsConfigCreate,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    cfg = SmsConfig(
        provider=payload.provider,
        sign_name=payload.sign_name,
        template_code=payload.template_code,
        access_key_id_encrypted=encrypt_credential(payload.access_key_id) if payload.access_key_id else None,
        access_key_secret_encrypted=encrypt_credential(payload.access_key_secret) if payload.access_key_secret else None,
        sdk_app_id=payload.sdk_app_id,
        region=payload.region,
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


@router.put("/{cfg_id}", response_model=SmsConfigResponse)
async def update_sms_config(
    cfg_id: UUID,
    payload: SmsConfigUpdate,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(SmsConfig).where(SmsConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")

    if payload.sign_name is not None:
        cfg.sign_name = payload.sign_name
    if payload.template_code is not None:
        cfg.template_code = payload.template_code
    if payload.sdk_app_id is not None:
        cfg.sdk_app_id = payload.sdk_app_id
    if payload.region is not None:
        cfg.region = payload.region
    if payload.daily_limit is not None:
        cfg.daily_limit = payload.daily_limit
    if payload.interval_seconds is not None:
        cfg.interval_seconds = payload.interval_seconds
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
async def delete_sms_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(SmsConfig).where(SmsConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")
    if cfg.is_active:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="cannot_delete_active_config")
    await db.delete(cfg)
    await db.commit()


@router.post("/{cfg_id}/activate", response_model=SmsConfigResponse)
async def activate_sms_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(SmsConfig).where(SmsConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")

    # 先全部 deactivate，再 activate 当前（partial unique index 保证全局一行 is_active=true）
    await db.execute(update(SmsConfig).values(is_active=False))
    cfg.is_active = True
    await db.commit()
    await db.refresh(cfg)
    return _to_response(cfg)


@router.post("/{cfg_id}/deactivate", response_model=SmsConfigResponse)
async def deactivate_sms_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(SmsConfig).where(SmsConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")
    cfg.is_active = False
    await db.commit()
    await db.refresh(cfg)
    return _to_response(cfg)


@router.post("/{cfg_id}/test", response_model=TestResult)
async def test_sms_config(
    cfg_id: UUID,
    admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(SmsConfig).where(SmsConfig.id == cfg_id))
    cfg = result.scalar_one_or_none()
    if not cfg:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="config_not_found")

    probe_fn = get_probe(cfg.provider)
    if not probe_fn:
        return TestResult(ok=False, error=f"unknown_provider:{cfg.provider}")

    secrets = _decrypt_secrets(cfg)
    try:
        # provider 模块是同步 httpx 调用，包到 to_thread 避免阻塞 event loop
        await asyncio.to_thread(probe_fn, cfg, secrets)
        return TestResult(ok=True)
    except Exception as e:
        return TestResult(ok=False, error=str(e)[:200])
