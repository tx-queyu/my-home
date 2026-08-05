"""FastAPI 入口：lifespan 建表、CORS、路由装配。"""
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import (
    admin_courses,
    appliances,
    auth,
    courses,
    devices,
    email_configs,
    families,
    points,
    rewards,
    self_study,
    skills,
    sms_configs,
    system,
    tasks,
    tts,
)
from app.core.config import settings
from app.core.cors import configure_cors, parse_cors_origins
from app.core.database import async_session, engine
from app.core.seed_courses import seed_courses_if_empty
from app.core.seed_words import seed_words_if_empty


@asynccontextmanager
async def lifespan(app: FastAPI):
    from app.models import Base

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    async with async_session() as db:
        await seed_courses_if_empty(db)
        await seed_words_if_empty(db)
    try:
        yield
    finally:
        await engine.dispose()


app = FastAPI(title=settings.app_name, version="0.1.0", lifespan=lifespan)

configure_cors(app, parse_cors_origins(settings.cors_origins))

app.include_router(auth.router)
app.include_router(families.router)
app.include_router(appliances.router)
app.include_router(courses.router)
app.include_router(admin_courses.router)
app.include_router(tasks.router)
app.include_router(points.router)
app.include_router(rewards.router)
app.include_router(skills.router)
app.include_router(self_study.router)
app.include_router(devices.router)
app.include_router(system.router)
app.include_router(sms_configs.router)
app.include_router(email_configs.router)
app.include_router(tts.router)


@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.app_name}
