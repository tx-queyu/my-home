"""华为云 Cloud Email 探活 + 发码 — httpx + stdlib HMAC-SHA256 直接调 RESTful API。

复用 sms_providers/huawei_provider.py 的签名 pattern（SDK-HMAC-SHA256，不派生签名密钥）。
- 探活调 IAM ListUsers API（limit=1）验证 AK/SK 可用
- 发码调 cloudemail 发送 API（POST /v1/cloudemail/email-body/xphone/v2/send）
"""

import hashlib
import hmac
import json
from datetime import datetime, timezone

import httpx


def _sign_request(
    *,
    method: str,
    host: str,
    path: str,
    query: str,
    body_bytes: bytes,
    ak: str,
    sk: str,
) -> tuple[str, str, str]:
    """华为云 SDK-HMAC-SHA256 签名。返回 (timestamp, payload_hash, auth_header)。"""
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    payload_hash = hashlib.sha256(body_bytes).hexdigest()

    canonical_headers = (
        f"host: {host}\n"
        f"x-sdk-content-sha256: {payload_hash}\n"
        f"x-sdk-date: {timestamp}\n"
    )
    signed_headers = "host;x-sdk-content-sha256;x-sdk-date"
    canonical_request = (
        f"{method}\n{path}\n{query}\n{canonical_headers}\n{signed_headers}\n{payload_hash}"
    )

    string_to_sign = (
        f"SDK-HMAC-SHA256\n{timestamp}\n"
        + hashlib.sha256(canonical_request.encode()).hexdigest()
    )
    signature = hmac.new(sk.encode(), string_to_sign.encode(), hashlib.sha256).hexdigest()
    auth_header = (
        f"SDK-HMAC-SHA256 Access={ak}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )
    return timestamp, payload_hash, auth_header


def probe(cfg, secrets: dict) -> None:
    """华为云探活。secrets = {"access_key_id", "access_key_secret"}。

    调 IAM ListUsers API（GET /v3/users?limit=1）验证 AK/SK 可用。
    """
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    if not ak or not sk:
        raise Exception("AK/SK 未配置")

    host = "iam.myhuaweicloud.com"
    path = "/v3/users"
    query = "limit=1"

    timestamp, payload_hash, auth_header = _sign_request(
        method="GET",
        host=host,
        path=path,
        query=query,
        body_bytes=b"",
        ak=ak,
        sk=sk,
    )

    resp = httpx.get(
        f"https://{host}{path}?{query}",
        headers={
            "Host": host,
            "x-sdk-content-sha256": payload_hash,
            "x-sdk-date": timestamp,
            "Authorization": auth_header,
        },
        timeout=15.0,
    )
    if resp.status_code in (401, 403):
        raise Exception(f"Authentication failed (status={resp.status_code})")
    if resp.status_code >= 400:
        raise Exception(f"HTTP {resp.status_code}: {resp.text[:200]}")


def send(cfg, secrets: dict, to: str, code: str) -> None:
    """华为云 Cloud Email 发码。secrets = {"access_key_id", "access_key_secret"}。"""
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    if not cfg.from_email:
        raise Exception("发件人邮箱未配置")

    region = cfg.region or "cn-north-4"
    host = f"cloudemail.{region}.myhuaweicloud.com"
    path = "/v1/cloudemail/email-body/xphone/v2/send"

    subject = "您的验证码"
    body_html = f'<p>您的验证码是：<strong>{code}</strong>，10 分钟内有效。</p><p>如非本人操作，请忽略本邮件。</p>'

    body = {
        "from_mail": cfg.from_email,
        "to_mail": to,
        "subject": subject,
        "body_html": body_html,
    }
    if cfg.from_name:
        body["from_name"] = cfg.from_name
    body_bytes = json.dumps(body, separators=(",", ":")).encode()

    timestamp, payload_hash, auth_header = _sign_request(
        method="POST",
        host=host,
        path=path,
        query="",
        body_bytes=body_bytes,
        ak=ak,
        sk=sk,
    )

    resp = httpx.post(
        f"https://{host}{path}",
        content=body_bytes,
        headers={
            "Host": host,
            "Content-Type": "application/json",
            "x-sdk-content-sha256": payload_hash,
            "x-sdk-date": timestamp,
            "Authorization": auth_header,
        },
        timeout=15.0,
    )
    if resp.status_code in (401, 403):
        raise Exception(f"Authentication failed (status={resp.status_code})")
    if resp.status_code >= 400:
        raise Exception(f"HTTP {resp.status_code}: {resp.text[:200]}")
    try:
        result = resp.json()
        if result.get("code") not in (0, "0"):
            raise Exception(f"huawei send_email failed: {result.get('description', result)}")
    except json.JSONDecodeError:
        raise Exception(f"huawei send_email invalid response: {resp.text[:200]}")
