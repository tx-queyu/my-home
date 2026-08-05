"""腾讯云 SES 探活 + 发码 — httpx + TC3-HMAC-SHA256 v3 签名（无 SDK 依赖）。

调 SendEmail API（ses.tencentcloudapi.com）。
TC3 签名同 SMS provider：派生 SigningKey 后 HMAC-SHA256。
"""

import hashlib
import hmac
import json
from datetime import datetime, timezone

import httpx


def _tc3_sign(
    *,
    service: str,
    ak: str,
    sk: str,
    host: str,
    method: str,
    path: str,
    query: str,
    headers: dict,
    body_bytes: bytes,
) -> tuple[str, str]:
    """TC3-HMAC-SHA256 v3 签名。返回 (timestamp, authorization_header)。"""
    timestamp_str = str(int(datetime.now(timezone.utc).timestamp()))
    payload_hash = hashlib.sha256(body_bytes).hexdigest()

    sorted_header_keys = sorted([k.lower() for k in headers.keys()])
    canonical_headers = ""
    signed_headers = ""
    for k in sorted_header_keys:
        v = headers[k]
        canonical_headers += f"{k}:{v.strip()}\n"
        signed_headers += f"{k};"
    signed_headers = signed_headers.rstrip(";")

    canonical_request = (
        f"{method.upper()}\n{path}\n{query}\n{canonical_headers}\n{signed_headers}\n{payload_hash}"
    )

    date_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    credential_scope = f"{date_str}/{service}/tc3_request"
    string_to_sign = (
        f"TC3-HMAC-SHA256\n{timestamp_str}\n{credential_scope}\n"
        + hashlib.sha256(canonical_request.encode()).hexdigest()
    )

    secret_date = hmac.new(("TC3" + sk).encode(), date_str.encode(), hashlib.sha256).digest()
    secret_service = hmac.new(secret_date, service.encode(), hashlib.sha256).digest()
    secret_signing = hmac.new(secret_service, b"tc3_request", hashlib.sha256).digest()
    signature = hmac.new(secret_signing, string_to_sign.encode(), hashlib.sha256).hexdigest()

    authorization = (
        f"TC3-HMAC-SHA256 Credential={ak}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )
    return timestamp_str, authorization


def probe(cfg, secrets: dict) -> None:
    """腾讯云 SES 探活 — 简化方案：仅校验 AK/SK 非空。"""
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    if not ak or not sk:
        raise Exception("AK/SK 未配置")
    if not cfg.from_email:
        raise Exception("发件人邮箱未配置")


def send(cfg, secrets: dict, to: str, code: str) -> None:
    """腾讯云 SES 发码。secrets = {"access_key_id", "access_key_secret"}。"""
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    host = "ses.tencentcloudapi.com"
    path = "/"
    subject = "您的验证码"
    html_body = f'<p>您的验证码是：<strong>{code}</strong>，10 分钟内有效。</p><p>如非本人操作，请忽略本邮件。</p>'

    body = {
        "FromEmailAddress": cfg.from_email,
        "Destination": [to],
        "Subject": subject,
        "HtmlBody": html_body,
    }
    body_bytes = json.dumps(body, separators=(",", ":")).encode()

    timestamp_str, authorization = _tc3_sign(
        service="ses",
        ak=ak,
        sk=sk,
        host=host,
        method="POST",
        path=path,
        query="",
        headers={
            "content-type": "application/json; charset=utf-8",
            "host": host,
            "x-tc-action": "SendEmail",
        },
        body_bytes=body_bytes,
    )

    resp = httpx.post(
        f"https://{host}{path}",
        content=body_bytes,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Host": host,
            "X-TC-Action": "SendEmail",
            "X-TC-Version": "2020-10-02",
            "X-TC-Timestamp": timestamp_str,
            "Authorization": authorization,
        },
        timeout=15.0,
    )
    if resp.status_code in (401, 403):
        raise Exception(f"Authentication failed (status={resp.status_code})")
    if resp.status_code >= 400:
        raise Exception(f"HTTP {resp.status_code}: {resp.text[:200]}")
    try:
        result = resp.json()
    except Exception:
        raise Exception(f"tencent email invalid response: {resp.text[:200]}")
    response = result.get("Response", {})
    if "Error" in response:
        err = response["Error"]
        raise Exception(f"tencent send_email failed: {err.get('Message', err)}")
    if not response.get("MessageId"):
        raise Exception(f"tencent send_email failed: {response}")
