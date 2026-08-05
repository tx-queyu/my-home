"""Edge TTS 代理 —— Microsoft Edge 浏览器 TTS 服务的非官方 Python 客户端。

用途：给孩子朗读练习提供高质量英式英语发音（默认 en-GB-SoniaNeural），
比 Android 自带 TextToSpeech.engine 更自然，发音更标准。

接入：
- 库：edge-tts（https://github.com/rany2/edge-tts），无 API key，直接走微软公共 endpoint
- 协议：WebSocket 流式 ssml → mp3 chunks
- 同步流：async generator yield mp3 bytes（直接给 FastAPI StreamingResponse）

voice 选择（按语言+性别）：
- en-GB-SoniaNeural：英国英语女声（默认，应用于朗读练习）
- en-GB-RyanNeural：英国英语男声
- en-US-AriaNeural：美国英语女声
- zh-CN-XiaoxiaoNeural：中国大陆普通话女声（用于中文文案"我们下面开始朗读练习"等）

注意：
- edge-tts 是长连接 WS，单次请求阻塞；用 asyncio.wait_for 设超时
- 大文本自动分段，单次最长 ~31s（mp3 stream），可截断
"""
from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass

import edge_tts

logger = logging.getLogger(__name__)

DEFAULT_VOICE_EN_GB = "en-GB-SoniaNeural"
DEFAULT_VOICE_ZH_CN = "zh-CN-XiaoxiaoNeural"

# 单次合成最长时长（秒），edge-tts 服务端上限约 31s，留 buffer
TTS_TIMEOUT_SECONDS = 30.0


class EdgeTtsError(Exception):
    def __init__(self, code: str, message: str = ""):
        self.code = code
        self.message = message or code
        super().__init__(self.message)


@dataclass
class SynthRequest:
    text: str
    voice: str = DEFAULT_VOICE_EN_GB
    rate: str = "+0%"   # 语速：-50% 慢 / +0% 正常 / +20% 稍快
    volume: str = "+0%"  # 音量：-50% 轻 / +0% 正常 / +20% 响


def _pick_voice(text: str, override: str | None) -> str:
    """按文本语言自动选 voice；显式 override 优先。"""
    if override:
        return override
    has_cjk = any("一" <= ch <= "鿿" for ch in text)
    return DEFAULT_VOICE_ZH_CN if has_cjk else DEFAULT_VOICE_EN_GB


async def synthesize_mp3(req: SynthRequest) -> bytes:
    """同步合成完整 mp3 bytes。失败抛 EdgeTtsError。

    用于一次性播放短句（朗读练习的提示语 / 单词）。
    """
    if not req.text or not req.text.strip():
        raise EdgeTtsError("empty_text", "tts text 为空")
    voice = _pick_voice(req.text, req.voice or None)

    async def _run() -> bytes:
        communicate = edge_tts.Communicate(
            text=req.text,
            voice=voice,
            rate=req.rate,
            volume=req.volume,
        )
        chunks: list[bytes] = []
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                chunks.append(chunk["data"])
        if not chunks:
            raise EdgeTtsError("no_audio", "edge-tts 返回空音频")
        return b"".join(chunks)

    try:
        return await asyncio.wait_for(_run(), timeout=TTS_TIMEOUT_SECONDS)
    except asyncio.TimeoutError:
        raise EdgeTtsError("timeout", "edge-tts 合成超时")
    except EdgeTtsError:
        raise
    except Exception as e:
        logger.warning("edge-tts 合成失败 text=%r voice=%s err=%s", req.text[:80], voice, e)
        raise EdgeTtsError("synth_failed", f"edge-tts 错误: {type(e).__name__}: {e}")


async def stream_mp3(req: SynthRequest):
    """异步 yield mp3 bytes chunks，配合 FastAPI StreamingResponse 用。

    用于流式播放长文本（降低首字节延迟）。
    """
    if not req.text or not req.text.strip():
        raise EdgeTtsError("empty_text", "tts text 为空")
    voice = _pick_voice(req.text, req.voice or None)

    try:
        communicate = edge_tts.Communicate(
            text=req.text,
            voice=voice,
            rate=req.rate,
            volume=req.volume,
        )
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                yield chunk["data"]
    except Exception as e:
        logger.warning("edge-tts 流式失败 text=%r voice=%s err=%s", req.text[:80], voice, e)
        raise EdgeTtsError("synth_failed", f"edge-tts 错误: {type(e).__name__}: {e}")
