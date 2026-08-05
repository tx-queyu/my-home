"""字段级加密 util — Fernet (AES-128-CBC + HMAC-SHA256) envelope。

key 来源：settings.crypto_key（prod 显式设置，dev 派生兜底）。
凭证只在内存短暂解密，绝不落 env / PVC / 日志。

用途：加密 SmsConfig.access_key_id_encrypted / EmailConfig.password_encrypted 等敏感字段。
Fernet 自带随机 IV 与 HMAC 完整性校验，相同明文每次加密产物不同且防篡改。
"""

from __future__ import annotations

import base64
import hashlib

from cryptography.fernet import Fernet, InvalidToken

from app.core.config import settings

# dev 兜底 key material（仅 dev；prod 由 _assert_prod_secrets 拦截空值）
_DEV_KEY_MATERIAL = b"myhome-credential-dev-key-do-not-use-in-prod"


def _load_fernet() -> Fernet:
    """按 settings.crypto_key 构造 Fernet。

    空值（dev）→ 用固定 material sha256 派生；非空 → sha256 派生为 32 字节 urlsafe base64。
    统一 sha256 派生以兼容任意长度口令（Fernet 要求 32 字节 urlsafe base64 key）。
    """
    raw = settings.crypto_key
    material = raw.encode("utf-8") if raw else _DEV_KEY_MATERIAL
    key = base64.urlsafe_b64encode(hashlib.sha256(material).digest())
    return Fernet(key)


def encrypt_credential(plaintext: str) -> str:
    """明文字符串 → Fernet token（str）。"""
    return _load_fernet().encrypt(plaintext.encode("utf-8")).decode("ascii")


def decrypt_credential(token: str) -> str:
    """Fernet token → 明文。InvalidToken → ValueError。"""
    try:
        return _load_fernet().decrypt(token.encode("ascii")).decode("utf-8")
    except InvalidToken as e:
        raise ValueError("凭证解密失败：token 无效或 key 不匹配") from e
