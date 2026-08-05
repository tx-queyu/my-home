"""阿里云 DirectMail 探活 + 发码 — httpx + POP RPC v1 签名（HMAC-SHA1，无 SDK 依赖）。

调 SingleSendMail API（dm.{region}.aliyuncs.com）。
POP RPC v1 签名同 SMS provider：HMAC-SHA1(base, AccessKeySecret + "&") → base64 → URL 编码。
"""

import base64
import hashlib
import hmac
import time
import urllib.parse
import uuid

import httpx


def _pop_signature(params: dict, access_key_secret: str) -> str:
    """POP RPC v1 签名（HMAC-SHA1）。同 SMS provider，复用一份避免跨模块依赖。"""
    sorted_keys = sorted(params.keys())
    canonical_query = "&".join(
        f"{k}={urllib.parse.quote(str(params[k]), safe='~')}" for k in sorted_keys
    )
    string_to_sign = "POST&" + urllib.parse.quote("/", safe="") + "&" + urllib.parse.quote(canonical_query, safe="")
    digest = hmac.new(
        (access_key_secret + "&").encode("utf-8"),
        string_to_sign.encode("utf-8"),
        hashlib.sha1,
    ).digest()
    return base64.b64encode(digest).decode("ascii")


def probe(cfg, secrets: dict) -> None:
    """阿里云 DirectMail 探活。

    简化方案：仅校验 AK/SK 非空，不实际调 list API（DM list API 资源粒度复杂）。
    admin 在 UI 点「测试」时主要靠后续真实发邮件验证。
    """
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    if not ak or not sk:
        raise Exception("AK/SK 未配置")
    if not cfg.from_email:
        raise Exception("发件人邮箱未配置")


def send(cfg, secrets: dict, to: str, code: str) -> None:
    """阿里云 DirectMail 发码。secrets = {"access_key_id", "access_key_secret"}。"""
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    region = cfg.region or "cn-hangzhou"
    host = f"dm.{region}.aliyuncs.com"

    subject = "您的验证码"
    html_body = f'<p>您的验证码是：<strong>{code}</strong>，10 分钟内有效。</p><p>如非本人操作，请忽略本邮件。</p>'

    params = {
        "AccessKeyId": ak,
        "Action": "SingleSendMail",
        "AccountName": cfg.from_email,
        "AddressType": "1",
        "Format": "JSON",
        "FromAlias": cfg.from_name or "",
        "HtmlBody": html_body,
        "ReplyToAddress": "true",
        "RegionId": region,
        "SignatureMethod": "HMAC-SHA1",
        "SignatureNonce": uuid.uuid4().hex,
        "SignatureVersion": "1.0",
        "Subject": subject,
        "ToAddress": to,
        "Timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "Version": "2015-11-23",
    }
    params["Signature"] = _pop_signature(params, sk)

    resp = httpx.post(
        f"https://{host}",
        data=params,
        timeout=15.0,
    )
    if resp.status_code >= 400:
        raise Exception(f"aliyun email HTTP {resp.status_code}: {resp.text[:200]}")
    try:
        result = resp.json()
    except Exception:
        raise Exception(f"aliyun email invalid response: {resp.text[:200]}")
    if result.get("Code") and result.get("Code") != "OK":
        raise Exception(f"aliyun single_send_mail failed: {result.get('Message', result)}")
