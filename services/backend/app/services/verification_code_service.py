"""验证码生成 + 存储 + 校验。

6 位数字 OTP，bcrypt hash 存 verification_codes 表（不存明文，防 DB dump 泄露）。
10min 有效，5 次错误失效（防爆破 — 6 位数字 10^6，5 次试错 + IP 限速 → 爆破不可行）。
verify 成功直接消耗（consumed_at 标记），同时签发 10min verify_token JWT — 后续注册/
登录/改绑/重置密码用此 token，不引入 VerificationTicket 表（简化）。
"""

import secrets
from datetime import datetime, timedelta, timezone
from uuid import uuid4

from jose import jwt
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import hash_password, verify_password
from app.models import VerificationCode


def _generate_code() -> str:
    """CSPRNG 生成 6 位数字（000000-999999）。

    secrets.randbelow 是 CSPRNG（基于 os.urandom），不可预测。
    """
    return str(secrets.randbelow(1000000)).zfill(6)


async def issue_code(
    db: AsyncSession,
    *,
    channel: str,
    target: str,
    purpose: str,
    ip: str | None,
) -> str:
    """生成验证码 + 落库。返回明文 code（调用方负责发送到用户）。

    code_hash 用 bcrypt 存（cost 12，与用户密码一致）。
    expires_at = now + 10min（settings.verification_code_ttl_minutes）。
    """
    code = _generate_code()
    record = VerificationCode(
        channel=channel,
        target=target,
        purpose=purpose,
        code_hash=hash_password(code),
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.verification_code_ttl_minutes),
        ip=ip,
    )
    db.add(record)
    await db.flush()
    return code


async def verify_and_consume(
    db: AsyncSession,
    *,
    channel: str,
    target: str,
    purpose: str,
    code: str,
) -> str | None:
    """校验 + 消耗验证码。

    成功 → 标记 code consumed_at + 签发 verify_token，返回 token 字符串。
    失败 → increment attempt_count，达 5 次标记 consumed_at（防爆破）；返回 None。

    5 次错误或过期后，该 code 不可再用 — 用户需重新发码。
    """
    stmt = (
        select(VerificationCode)
        .where(
            VerificationCode.channel == channel,
            VerificationCode.target == target,
            VerificationCode.purpose == purpose,
            VerificationCode.consumed_at.is_(None),
            VerificationCode.expires_at > datetime.now(timezone.utc),
        )
        .order_by(VerificationCode.created_at.desc())
        .limit(1)
    )
    record = (await db.execute(stmt)).scalar_one_or_none()

    if not record:
        return None

    if verify_password(code, record.code_hash):
        record.consumed_at = datetime.now(timezone.utc)
        await db.flush()
        return create_verify_token(
            target=target,
            purpose=purpose,
            channel=channel,
            code_id=str(record.id),
        )

    record.attempt_count = (record.attempt_count or 0) + 1
    if record.attempt_count >= settings.verification_code_max_attempts:
        record.consumed_at = datetime.now(timezone.utc)
    return None


async def invalidate_target_codes(db: AsyncSession, target: str) -> None:
    """改绑成功后调用 — 失效该 target 上所有未消费 code。

    防攻击者改完一个再改一个（拿到新 phone 的 verify_token 改完 phone 后，
    旧 phone 的所有 code 全失效）。
    """
    await db.execute(
        update(VerificationCode)
        .where(
            VerificationCode.target == target,
            VerificationCode.consumed_at.is_(None),
        )
        .values(consumed_at=datetime.now(timezone.utc))
    )


def create_verify_token(*, target: str, purpose: str, channel: str, code_id: str) -> str:
    """签发 verify_token（10min JWT，HS256）。

    payload: target / purpose / channel / code_id / type=verify / jti。
    不强制单次使用 — 简化方案接受 10min 内可重放（与 reset 链路同生命周期）。
    """
    expire = datetime.now(timezone.utc) + timedelta(minutes=settings.verify_token_ttl_minutes)
    payload = {
        "target": target,
        "purpose": purpose,
        "channel": channel,
        "code_id": code_id,
        "type": "verify",
        "exp": expire,
        "jti": uuid4().hex,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_verify_token(token: str) -> dict | None:
    """解析 verify_token。无效/过期/类型不符返回 None。"""
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
        if payload.get("type") != "verify":
            return None
        return payload
    except Exception:
        return None


async def check_rate_limit(
    db: AsyncSession,
    *,
    target: str,
    ip: str | None,
) -> bool:
    """内联限速：返回是否允许发码。

    规则：24h 内同一 target 发码次数 ≤ daily_target_limit；
    1h 内同一 IP 发码次数 ≤ hourly_ip_limit（防轰炸多个 target）。
    """
    now = datetime.now(timezone.utc)
    day_ago = now - timedelta(hours=24)
    hour_ago = now - timedelta(hours=1)

    target_count_stmt = (
        select(VerificationCode)
        .where(VerificationCode.target == target, VerificationCode.created_at > day_ago)
    )
    target_count = (await db.execute(target_count_stmt)).all().__len__()

    if target_count >= settings.verification_daily_target_limit:
        return False

    if ip:
        ip_count_stmt = (
            select(VerificationCode)
            .where(VerificationCode.ip == ip, VerificationCode.created_at > hour_ago)
        )
        ip_count = (await db.execute(ip_count_stmt)).all().__len__()
        if ip_count >= settings.verification_hourly_ip_limit:
            return False

    return True
