"""CORS 中间件装配 + 原始字符串解析。"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware


def parse_cors_origins(raw: str) -> list[str]:
    return [o.strip() for o in (raw or "").split(",") if o.strip()]


def configure_cors(app: FastAPI, origins: list[str]) -> None:
    """按白名单挂 CORS。通配 * 时不带 credentials（规范要求）。"""
    if not origins:
        return
    allow_credentials = "*" not in origins
    app.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=allow_credentials,
        allow_methods=["*"],
        allow_headers=["*"],
    )
