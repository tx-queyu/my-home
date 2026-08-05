"""Text-to-Speech 代理 API（Edge TTS）。

用途：Android 端朗读练习调用，由后端转发到 Edge TTS 服务，避免在客户端嵌入
edge-tts 库或暴露 Microsoft 公共 endpoint 给反编译。

接口：
- POST /api/tts：JSON body → mp3 bytes（一次性合成，适合短句）
- GET /api/tts?text=&voice=：查询参数 → 流式 mp3 chunks（适合长文本，低延迟）

鉴权：需要登录（get_current_user），避免被外部白嫖带宽。

voice 默认规则（在 edge_tts.py:_pick_voice）：
- 文本含 CJK 字符 → zh-CN-XiaoxiaoNeural（普通话女声）
- 否则 → en-GB-SoniaNeural（英国英语女声）—— 朗读练习单词默认走这个
"""
from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.core.security import get_current_user
from app.models import User
from app.services.edge_tts import (
    DEFAULT_VOICE_EN_GB,
    EdgeTtsError,
    SynthRequest,
    stream_mp3,
    synthesize_mp3,
)

router = APIRouter(prefix="/api/tts", tags=["tts"])


class TtsRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)
    voice: str | None = Field(default=None, max_length=64)
    rate: str = Field(default="+0%", max_length=8)
    volume: str = Field(default="+0%", max_length=8)


@router.post("")
async def synth_full(
    payload: TtsRequest,
    user: User = Depends(get_current_user),
):
    """一次性合成完整 mp3。返回 audio/mpeg，整体写入响应。

    适合短句（≤ 200 字符），首字节延迟 ~500ms。
    """
    try:
        mp3 = await synthesize_mp3(
            SynthRequest(
                text=payload.text,
                voice=payload.voice,
                rate=payload.rate,
                volume=payload.volume,
            )
        )
    except EdgeTtsError as e:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=f"tts_{e.code}")

    return StreamingResponse(
        iter([mp3]),
        media_type="audio/mpeg",
        headers={"Cache-Control": "no-store"},
    )


@router.get("")
async def synth_stream(
    text: str = Query(min_length=1, max_length=2000),
    voice: str | None = Query(default=None, max_length=64),
    rate: str = Query(default="+0%", max_length=8),
    volume: str = Query(default="+0%", max_length=8),
    user: User = Depends(get_current_user),
):
    """流式合成 mp3 chunks。返回 audio/mpeg，chunked transfer。

    适合长文本，首字节延迟 ~300ms，总时长与文本长度成正比。
    """
    req = SynthRequest(text=text, voice=voice, rate=rate, volume=volume)
    try:
        return StreamingResponse(
            stream_mp3(req),
            media_type="audio/mpeg",
            headers={"Cache-Control": "no-store"},
        )
    except EdgeTtsError as e:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=f"tts_{e.code}")


@router.get("/voices")
async def list_voices(
    user: User = Depends(get_current_user),
):
    """返回当前应用推荐的 voice 列表（节省客户端不必要遍历）。"""
    return {
        "voices": [
            {"id": DEFAULT_VOICE_EN_GB, "lang": "en-GB", "gender": "Female",
             "label": "英国英语女声（默认）"},
            {"id": "en-GB-RyanNeural", "lang": "en-GB", "gender": "Male",
             "label": "英国英语男声"},
            {"id": "en-US-AriaNeural", "lang": "en-US", "gender": "Female",
             "label": "美国英语女声"},
            {"id": "zh-CN-XiaoxiaoNeural", "lang": "zh-CN", "gender": "Female",
             "label": "普通话女声（中文文案）"},
        ]
    }
