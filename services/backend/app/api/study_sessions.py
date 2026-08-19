"""学习时长 API（v0.17.0）—— 互动 session 埋点上报 + 聚合统计。

- POST /api/study-sessions               任何登录用户上报（孩子任务/体验 + 家长自学）
- GET  /api/study-sessions               明细列表（双视角 + 日期过滤）
- GET  /api/study-sessions/stats         聚合统计（今日/本周/累计 + 按教材分布）
- 不给积分；user_id 服务端取 current_user.id，不信任 body
- 本表无 family_id：跨家庭防护靠 target user 显式同 family 校验 → 404 child_not_found
"""
from datetime import date, timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user
from app.models import StudySession, User, UserRole
from app.schemas.study_session import (
    StudySessionCreate,
    StudySessionOut,
    StudyStatsOut,
    TextbookTimeOut,
)

router = APIRouter(prefix="/api/study-sessions", tags=["study-sessions"])


async def _resolve_target_user(
    db: AsyncSession,
    current: User,
    user_id: UUID | None,
) -> User:
    """解析统计/明细的目标用户：
    - user_id 为 None → 自己
    - 孩子传他人 id → 403 parent_only
    - 家长传 user_id → 必须同家庭（跨家庭/不存在 → 404 child_not_found，不暴露存在性）
    """
    if user_id is None:
        return current
    if not ({UserRole.parent, UserRole.family_admin} & set(current.roles)):
        # 孩子：只能查自己
        if user_id != current.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="parent_only")
        return current
    # 家长：校验目标用户在本家庭
    if current.family_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="child_not_found")
    result = await db.execute(
        select(User).where(
            User.id == user_id,
            User.family_id == current.family_id,
            User.is_active.is_(True),
        )
    )
    target = result.scalar_one_or_none()
    if target is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="child_not_found")
    return target


@router.post("", response_model=StudySessionOut, status_code=status.HTTP_201_CREATED)
async def report_study_session(
    data: StudySessionCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    session = StudySession(
        user_id=user.id,
        subject=data.subject,
        textbook=data.textbook,
        learning_method=data.learning_method,
        session_type=data.session_type,
        source=data.source,
        duration_seconds=data.duration_seconds,
        session_date=data.session_date or date.today(),
    )
    db.add(session)
    await db.commit()
    await db.refresh(session)
    return session


# ↓ 注意：stats 路径必须在 /{...} 之前注册（这里无 /{id} 路径，仍按字面量优先惯例）。
@router.get("/stats", response_model=StudyStatsOut)
async def get_study_stats(
    user_id: UUID | None = Query(None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    target = await _resolve_target_user(db, user, user_id)
    today = date.today()
    week_start = today - timedelta(days=today.weekday())  # 周一为首日

    today_seconds = await db.scalar(
        select(func.coalesce(func.sum(StudySession.duration_seconds), 0)).where(
            StudySession.user_id == target.id, StudySession.session_date == today
        )
    )
    week_seconds = await db.scalar(
        select(func.coalesce(func.sum(StudySession.duration_seconds), 0)).where(
            StudySession.user_id == target.id, StudySession.session_date >= week_start
        )
    )
    total_seconds = await db.scalar(
        select(func.coalesce(func.sum(StudySession.duration_seconds), 0)).where(
            StudySession.user_id == target.id
        )
    )
    # 按教材分布（subject, textbook 维度聚合，按总时长降序）
    dist_rows = (
        await db.execute(
            select(
                StudySession.subject,
                StudySession.textbook,
                func.sum(StudySession.duration_seconds).label("total_seconds"),
                func.count(StudySession.id).label("session_count"),
            )
            .where(StudySession.user_id == target.id)
            .group_by(StudySession.subject, StudySession.textbook)
            .order_by(func.sum(StudySession.duration_seconds).desc())
        )
    ).all()
    by_textbook = [
        TextbookTimeOut(
            subject=row.subject,
            textbook=row.textbook,
            total_seconds=int(row.total_seconds or 0),
            session_count=int(row.session_count or 0),
        )
        for row in dist_rows
    ]
    return StudyStatsOut(
        today_seconds=int(today_seconds or 0),
        week_seconds=int(week_seconds or 0),
        total_seconds=int(total_seconds or 0),
        by_textbook=by_textbook,
    )


@router.get("", response_model=list[StudySessionOut])
async def list_study_sessions(
    user_id: UUID | None = Query(None),
    date_from: date | None = Query(None),
    date_to: date | None = Query(None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    target = await _resolve_target_user(db, user, user_id)
    stmt = (
        select(StudySession)
        .where(StudySession.user_id == target.id)
        .order_by(StudySession.created_at.desc())
        .limit(50)
    )
    if date_from is not None:
        stmt = stmt.where(StudySession.session_date >= date_from)
    if date_to is not None:
        stmt = stmt.where(StudySession.session_date <= date_to)
    result = await db.execute(stmt)
    return result.scalars().all()
