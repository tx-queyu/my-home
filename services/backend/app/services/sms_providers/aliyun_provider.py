"""阿里云 SMS 探活 + 发码 — httpx + POP RPC v1 签名（HMAC-SHA1）。

调 SendSms API（dysmsapi.aliyuncs.com）。
POP RPC v1 签名步骤：
  1. 所有参数按 key 字典序排序
  2. 拼 query: k1=v1&k2=v2...（value URL 编码，~ 不编码）
  3. signature base = POST&%2F&<URL 编码后的 query>
  4. HMAC-SHA1(base, AccessKeySecret + "&")
  5. base64 后 URL 编码加到 Signature 参数
"""

import base64
import hashlib
import hmac
import time
import urllib.parse
import uuid

import httpx


def _pop_signature(params: dict, access_key_secret: str) -> str:
    """POP RPC v1 签名（HMAC-SHA1）"""
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
    """阿里云 SMS 探活。

    简化方案：仅校验 AK/SK 非空，不实际调 list API（阿里云 list API 需要的资源粒度较复杂）。
    admin 在 UI 点「测试」时主要靠后续真实发码验证 AK/SK + 模板。
    """
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]
    if not ak or not sk:
        raise Exception("AK/SK 未配置")


def send(cfg, secrets: dict, phone: str, code: str) -> None:
    """阿里云 SMS 发码"""
    ak = secrets["access_key_id"]
    sk = secrets["access_key_secret"]

    params = {
        "AccessKeyId": ak,
        "Action": "SendSms",
        "Format": "JSON",
        "PhoneNumbers": phone if phone.startswith("+") else f"+86{phone}",
        "RegionId": cfg.region or "cn-hangzhou",
        "SignName": cfg.sign_name,
        "SignatureMethod": "HMAC-SHA1",
        "SignatureNonce": uuid.uuid4().hex,
        "SignatureVersion": "1.0",
        "TemplateCode": cfg.template_code,
        "TemplateParam": f'{{"code":"{code}"}}',
        "Timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "Version": "2017-05-25",
    }
    params["Signature"] = _pop_signature(params, sk)

    resp = httpx.post(
        "https://dysmsapi.aliyuncs.com",
        data=params,
        timeout=15.0,
    )
    if resp.status_code >= 400:
        raise Exception(f"aliyun sms HTTP {resp.status_code}: {resp.text[:200]}")
    try:
        result = resp.json()
    except Exception:
        raise Exception(f"aliyun sms invalid response: {resp.text[:200]}")
    if result.get("Code") != "OK":
        raise Exception(f"aliyun send_sms failed: {result.get('Message', result)}")
