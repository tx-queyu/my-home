"""腾讯云 SMS 探活 + 发码 — httpx + TC3-HMAC-SHA256 v3 签名。

调 SendSms API（sms.tencentcloudapi.com）。
TC3 签名步骤：
  1. canonical_request = METHOD\npath\nquery\nheaders\nsigned_headers\nhashed_payload
  2. string_to_sign = TC3-HMAC-SHA256\ntimestamp\nhashed_canonical_request
  3. derive key: DateKey = HMAC(SecretDate, "TC3" + Secret); ServiceKey = HMAC(DateKey, service); SigningKey = HMAC(ServiceKey, "tc3_request")
  4. signature = HMAC(SigningKey, string_to_sign)
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
    """TC3-HMAC-SHA256 v3 签名。返回 (timestamp, authorization_header)"""
    timestamp_str = str(int(datetime.now(timezone.utc).timestamp()))
    payload_hash = hashlib.sha256(body_bytes).hexdigest()

    # canonical_headers 按字典序排序，每个 header 后跟 \n
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
    """腾讯云 SMS 探活 — 简化方案：仅校验 AK/SK + SmsSdkAppId 非空"""
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    if not ak or not sk:
        raise Exception("AK/SK 未配置")
    if not cfg.sdk_app_id:
        raise Exception("SmsSdkAppId 未配置")


def send(cfg, secrets: dict, phone: str, code: str) -> None:
    """腾讯云 SMS 发码"""
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    host = "sms.tencentcloudapi.com"
    path = "/"
    body = {
        "SmsSdkAppId": cfg.sdk_app_id,
        "SignName": cfg.sign_name,
        "TemplateId": cfg.template_code,
        "PhoneNumberSet": [phone if phone.startswith("+") else f"+86{phone}"],
        "TemplateParamSet": [code],
    }
    body_bytes = json.dumps(body, separators=(",", ":")).encode()

    timestamp_str, authorization = _tc3_sign(
        service="sms",
        ak=ak,
        sk=sk,
        host=host,
        method="POST",
        path=path,
        query="",
        headers={
            "content-type": "application/json; charset=utf-8",
            "host": host,
            "x-tc-action": "SendSms",
        },
        body_bytes=body_bytes,
    )

    resp = httpx.post(
        f"https://{host}{path}",
        content=body_bytes,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Host": host,
            "X-TC-Action": "SendSms",
            "X-TC-Version": "2021-01-11",
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
        raise Exception(f"tencent sms invalid response: {resp.text[:200]}")
    # 腾讯云 v3 包装：{"Response": {"SendStatusSet": [{"Code": "Ok", ...}]}}
    response = result.get("Response", {})
    if "Error" in response:
        err = response["Error"]
        raise Exception(f"tencent send_sms failed: {err.get('Message', err)}")
    send_status_set = response.get("SendStatusSet", [])
    if not send_status_set or send_status_set[0].get("Code") != "Ok":
        raise Exception(f"tencent send_sms failed: {send_status_set}")
