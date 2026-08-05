"""讯飞语音评测（流式版）WebSocket 客户端。

用途：给孩子朗读单词录音打分，返回 0-100 评分。专为中国人学英语设计，对 child voice 友好。

接入规范（参考 https://www.xfyun.cn/doc/Ise/IseAPI.html）：
- URL: wss://ise-api.xfyun.cn/v2/open-ise
- 协议：多帧 WebSocket
  - 第 1 帧（参数上传）：cmd=ssb, data.status=0
  - 第 2..N-1 帧（音频中间帧）：cmd=auw, aus=2, data.status=1
  - 第 N 帧（音频末帧）：cmd=auw, aus=4, data.status=2
- 鉴权：URL query 三件套 host/date/authorization，签名规则
  signature_origin = "host: {host}\\ndate: {date}\\nGET {path} HTTP/1.1"
  signature = base64(hmac_sha256(signature_origin, api_secret))
  authorization_origin = 'api_key="...", algorithm="hmac-sha256", headers="host date request-line", signature="..."'
  authorization = base64(authorization_origin)
"""
from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import logging
from datetime import datetime, timezone
from email.utils import format_datetime
from urllib.parse import urlencode
from xml.etree import ElementTree as ET

import websockets

logger = logging.getLogger(__name__)

ISE_WS_HOST = "ise-api.xfyun.cn"
ISE_WS_PATH = "/v2/open-ise"

# 每帧音频字节数（建议 1280B，最大 19200B；用 16KB 兼容性能与帧数）
AUDIO_CHUNK_BYTES = 16000


class IflytekIseError(Exception):
    """讯飞 ISE 调用异常。code 字段为机器可读 snake_case。"""

    def __init__(self, code: str, message: str = ""):
        self.code = code
        self.message = message or code
        super().__init__(self.message)


class AssessmentResult:
    """评测结果。score: 0-100；raw_xml: 原始 XML（debug 用）。"""

    def __init__(self, score: int, raw_xml: str = ""):
        self.score = score
        self.raw_xml = raw_xml


def _build_auth_url(api_key: str, api_secret: str) -> str:
    """生成带 HMAC-SHA256 签名的 WebSocket URL。

    讯飞标准签名格式：
      signature_origin = 'host: {host}\\ndate: {date}\\nGET {path} HTTP/1.1'（无尾换行）
      signature = base64(hmac_sha256(signature_origin, api_secret))
      authorization_origin = 'api_key="...", algorithm="hmac-sha256", headers="host date request-line", signature="..."'
      authorization = base64(authorization_origin)
    """
    now = datetime.now(timezone.utc)
    date_str = format_datetime(now, usegmt=True)

    signature_origin = (
        f"host: {ISE_WS_HOST}\n"
        f"date: {date_str}\n"
        f"GET {ISE_WS_PATH} HTTP/1.1"
    )
    hmac_digest = hmac.new(
        api_secret.encode("utf-8"),
        signature_origin.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    signature_b64 = base64.b64encode(hmac_digest).decode("utf-8")

    authorization_origin = (
        f'api_key="{api_key}", algorithm="hmac-sha256", '
        f'headers="host date request-line", signature="{signature_b64}"'
    )
    auth_b64 = base64.b64encode(authorization_origin.encode("utf-8")).decode("utf-8")

    params = {
        "authorization": auth_b64,
        "date": date_str,
        "host": ISE_WS_HOST,
    }
    return f"wss://{ISE_WS_HOST}{ISE_WS_PATH}?{urlencode(params)}"


def _parse_score(xml_text: str) -> int | None:
    """从 ISE XML 结果提取 total_score。

    ISE 流式版把分数放在元素属性上，例如：
      <read_word lan="en" type="study" version="7.0.0.1020">
        <rec_paper>
          <read_word total_score="0.173826" accuracy_score="..." is_rejected="true" ...>

    全树扫描属性 total_score，取第一个非空数字值，向下取整为 int。
    """
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return None
    for elem in root.iter():
        val = elem.attrib.get("total_score")
        if val and val.strip().replace(".", "").isdigit():
            try:
                return int(float(val.strip()))
            except ValueError:
                continue
    return None


async def assess_word(
    audio_pcm: bytes,
    ref_text: str,
    app_id: str,
    api_key: str,
    api_secret: str,
    category: str = "read_word",
    timeout: float = 20.0,
) -> AssessmentResult:
    """调 ISE 流式版评测英文发音。

    audio_pcm: 16kHz 16bit mono PCM raw bytes（AudioRecord 直接读出来的格式）
    ref_text: 参考文本
        - category=read_word: 单词拼写，如 "apple"
        - category=read_sentence: 句子/连读文本，如 "D O G dog"
    category: 评测题型
        - read_word（默认）：单词朗读，paper 格式 [word]\n<spelling>
        - read_sentence：句子朗读，paper 格式 [sent]\n<text>，适合拼+读连读评分
    """
    if not all([app_id, api_key, api_secret]):
        raise IflytekIseError("not_configured", "讯飞 ISE 未配置 APP_ID/API_KEY/API_SECRET")
    if not audio_pcm:
        raise IflytekIseError("no_audio", "audio 数据为空")
    if not ref_text:
        raise IflytekIseError("no_ref_text", "ref_text 为空")
    if category not in ("read_word", "read_sentence"):
        raise IflytekIseError("invalid_category", f"不支持的 category: {category}")

    url = _build_auth_url(api_key, api_secret)

    # ISE paper 格式按 category 切换：
    # - read_word:   [word]\n<spelling>    （单词朗读，每行一个词）
    # - read_sentence: [sent]\n<sentence>  （句子朗读，整段一个）
    # 文档：https://www.xfyun.cn/doc/Ise/IseAPI.html
    paper_tag = "sent" if category == "read_sentence" else "word"
    ise_paper = f"[{paper_tag}]\n{ref_text}"
    text_with_bom = "﻿" + ise_paper

    async def _run() -> AssessmentResult:
        async with websockets.connect(
            url,
            max_size=None,
            ping_interval=10,
            ping_timeout=10,
        ) as ws:
            # Frame 1: 参数上传（ssb）
            # 英文百分制评分必须 rst=entirety + ise_unite=1 + extra_ability=multi_dimension
            # 否则 ISE 返回的是原始模型回归值（如 4.9）而不是 0-100 百分制（如 80）
            # 参考 https://www.xfyun.cn/doc/Ise/IseAPI.html#业务参数说明business
            ssb_frame = {
                "common": {"app_id": app_id},
                "business": {
                    "sub": "ise",
                    "ent": "en_vip",  # 英文
                    "category": category,
                    "cmd": "ssb",
                    "text": text_with_bom,
                    "tte": "utf-8",
                    "ttp_skip": True,
                    "aue": "raw",
                    "auf": "audio/L16;rate=16000",
                    "rstcd": "utf8",
                    "rst": "entirety",
                    "ise_unite": "1",
                    "extra_ability": "multi_dimension",
                    "plev": "0",
                },
                "data": {"status": 0},
            }
            await asyncio.wait_for(ws.send(json.dumps(ssb_frame)), timeout=timeout)

            # Frame 2..N: 音频分帧上传
            total = len(audio_pcm)
            offset = 0
            frame_idx = 0
            while offset < total:
                chunk = audio_pcm[offset:offset + AUDIO_CHUNK_BYTES]
                is_last = offset + AUDIO_CHUNK_BYTES >= total
                if frame_idx == 0:
                    aus = 1  # 第一帧音频
                elif is_last:
                    aus = 4  # 最后一帧音频
                else:
                    aus = 2  # 中间帧
                status = 2 if is_last else 1
                auw_frame = {
                    "business": {"cmd": "auw", "aus": aus},
                    "data": {
                        "status": status,
                        "data": base64.b64encode(chunk).decode("utf-8"),
                    },
                }
                await asyncio.wait_for(ws.send(json.dumps(auw_frame)), timeout=timeout)
                offset += AUDIO_CHUNK_BYTES
                frame_idx += 1

            # 如果音频只有一帧，上面循环里 aus=1 + status=2 已合并在同一帧；
            # 如果音频为多帧，最后帧 aus=4 + status=2。两种情况都满足协议。

            # 接收结果（ISE 会一直返回帧直到 status=2 + 完整结果）
            final_xml_b64 = ""
            final_status = None
            while True:
                raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
                try:
                    msg = json.loads(raw)
                except (json.JSONDecodeError, TypeError):
                    raise IflytekIseError("invalid_response", f"ISE 响应非 JSON: {raw!r:.200}")

                code = msg.get("code")
                if code != 0:
                    raise IflytekIseError(f"code_{code}", f"ISE 业务错误: {msg.get('message', '')}")

                data = msg.get("data", {}) or {}
                if data.get("data"):
                    final_xml_b64 = data["data"]
                if data.get("status") is not None:
                    final_status = data["status"]
                if final_status == 2 and final_xml_b64:
                    break

            try:
                xml_text = base64.b64decode(final_xml_b64).decode("utf-8")
            except Exception:
                raise IflytekIseError("invalid_xml_encoding")

            score = _parse_score(xml_text)
            if score is None:
                logger.warning("ISE 响应无法解析 total_score: %s", xml_text[:500])
                raise IflytekIseError("score_not_found", "ISE XML 中未找到 total_score")

            logger.info("ISE 评测结果 score=%d xml=%s", score, xml_text[:800])
            return AssessmentResult(score=score, raw_xml=xml_text)

    try:
        return await _run()
    except asyncio.TimeoutError:
        raise IflytekIseError("timeout", "ISE WebSocket 超时")
    except websockets.exceptions.InvalidStatus as e:
        status = getattr(e.response, "status_code", "?")
        body = getattr(e.response, "body", b"")
        raise IflytekIseError("ws_status", f"ISE HTTP {status}: {body!r:.200}")
    except IflytekIseError:
        raise
    except Exception as e:
        raise IflytekIseError("ws_error", f"ISE WebSocket 错误: {type(e).__name__}: {e}")
