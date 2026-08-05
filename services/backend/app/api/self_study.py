"""家长自学教材 API(v0.16.1)。

3 个端点(任何登录用户可用,数据按 user_id 隔离):
- GET  /api/self-study/textbooks            我的教材清单(含各教材 active 课程)
- GET  /api/self-study/textbooks/available  可添加的教材(系统 active 课程聚合)
- POST /api/self-study/textbooks            添加教材 {subject, textbook}
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user
from app.models import Course, SelfStudyTextbook, User
from app.schemas.self_study import (
    SelfStudyTextbookCreate,
    SelfStudyTextbookOut,
    TextbookOptionOut,
)

router = APIRouter(prefix="/api/self-study", tags=["self-study"])


async def _courses_of(db: AsyncSession, subject: str, textbook: str) -> list[Course]:
    result = await db.execute(
        select(Course)
        .where(
            Course.subject == subject,
            Course.textbook == textbook,
            Course.is_active.is_(True),
        )
        .order_by(Course.sort_order)
    )
    return list(result.scalars().all())


@router.get("/textbooks", response_model=list[SelfStudyTextbookOut])
async def list_my_textbooks(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """我的自学教材清单,每个教材带 active 课程列表。"""
    rows = (
        await db.execute(
            select(SelfStudyTextbook)
            .where(SelfStudyTextbook.user_id == user.id)
            .order_by(SelfStudyTextbook.created_at)
        )
    ).scalars().all()
    out: list[SelfStudyTextbookOut] = []
    for row in rows:
        courses = await _courses_of(db, row.subject, row.textbook)
        out.append(
            SelfStudyTextbookOut(
                id=row.id, subject=row.subject, textbook=row.textbook, courses=courses
            )
        )
    return out


@router.get("/textbooks/available", response_model=list[TextbookOptionOut])
async def list_available_textbooks(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """系统里可自学的教材(active 课程按 subject+textbook 聚合)。"""
    courses = (
        await db.execute(
            select(Course)
            .where(Course.is_active.is_(True))
            .order_by(Course.subject, Course.textbook, Course.sort_order)
        )
    ).scalars().all()
    grouped: dict[tuple[str, str], list[Course]] = {}
    for c in courses:
        grouped.setdefault((c.subject, c.textbook), []).append(c)
    return [
        TextbookOptionOut(subject=s, textbook=t, courses=cs)
        for (s, t), cs in grouped.items()
    ]


@router.post(
    "/textbooks",
    response_model=SelfStudyTextbookOut,
    status_code=status.HTTP_201_CREATED,
)
async def add_textbook(
    body: SelfStudyTextbookCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """添加教材到我的自学清单。教材需有 active 课程;重复添加 409。"""
    courses = await _courses_of(db, body.subject, body.textbook)
    if not courses:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail="textbook_not_found"
        )
    existing = (
        await db.execute(
            select(SelfStudyTextbook).where(
                SelfStudyTextbook.user_id == user.id,
                SelfStudyTextbook.subject == body.subject,
                SelfStudyTextbook.textbook == body.textbook,
            )
        )
    ).scalar_one_or_none()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="textbook_already_added"
        )
    row = SelfStudyTextbook(
        user_id=user.id, subject=body.subject, textbook=body.textbook
    )
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return SelfStudyTextbookOut(
        id=row.id, subject=row.subject, textbook=row.textbook, courses=courses
    )
