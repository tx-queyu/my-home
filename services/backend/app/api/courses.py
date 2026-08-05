"""课程目录只读 API（系统预置，无 CRUD）。"""
import random
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import get_current_user
from app.models import ChildWordMastery, Course, User, Word
from app.schemas.course import CourseOut
from app.schemas.word import WordOut
from app.schemas.word_assessment import WordAssessmentResult, WordScoreIn, WordScoreOut
from app.services.iflytek_ise import IflytekIseError, assess_word as iflytek_assess
from app.services.mastery import upsert_mastery

import logging
logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/courses", tags=["courses"])

VALID_MODES = {"adaptive", "learn", "review", "random", "assess"}


async def _get_course(db: AsyncSession, course_id: UUID) -> Course:
    result = await db.execute(select(Course).where(Course.id == course_id))
    course = result.scalar_one_or_none()
    if not course:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="course_not_found")
    return course


@router.get("", response_model=list[CourseOut])
async def list_courses(
    subject: str | None = Query(None, max_length=32),
    textbook: str | None = Query(None, max_length=64),
    learning_method: str | None = Query(None, max_length=32),
    include_inactive: bool = Query(False),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Course)
    if not include_inactive:
        stmt = stmt.where(Course.is_active.is_(True))
    if subject:
        stmt = stmt.where(Course.subject == subject)
    if textbook:
        stmt = stmt.where(Course.textbook == textbook)
    if learning_method:
        stmt = stmt.where(Course.learning_method == learning_method)
    stmt = stmt.order_by(Course.subject, Course.textbook, Course.sort_order, Course.learning_method)
    result = await db.execute(stmt)
    return result.scalars().all()


@router.get("/{course_id}", response_model=CourseOut)
async def get_course(
    course_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await _get_course(db, course_id)


@router.get("/{course_id}/words", response_model=list[WordOut])
async def list_course_words(
    course_id: UUID,
    include_inactive: bool = Query(False),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """返回该课程的所有单词（按 sort_order 升序）。

    前端拉一次缓存本地随机挑词，避免每次 next 都网络请求。
    """
    await _get_course(db, course_id)  # 404 if course not found
    stmt = select(Word).where(Word.course_id == course_id)
    if not include_inactive:
        stmt = stmt.where(Word.is_active.is_(True))
    stmt = stmt.order_by(Word.sort_order, Word.spelling)
    result = await db.execute(stmt)
    return result.scalars().all()


@router.get("/{course_id}/words/next", response_model=list[WordOut])
async def list_next_words(
    course_id: UUID,
    limit: int = Query(10, ge=1, le=50),
    mode: str = Query("adaptive", max_length=16),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """自适应选词。

    - adaptive (默认): 70% 学习(mastery 最低) + 30% 复习(mastery >= 0.5 但最久未练)
    - learn: 全量按 mastery 升序（未评估的排最前）
    - review: 仅 mastery >= 0.5 的词,按 last_assessed_at 升序
    - random: 完全随机（兼容旧行为,不需要 mastery）
    - assess: 分层抽样（测评课用）,4 个能力带(new/learning/familiar/mastered)各取约 1/4,不足互补

    朗读任务模式和体验模式默认走 adaptive,后续可加 UI 开关让家长/孩子选 learn/review/random。
    """
    if mode not in VALID_MODES:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="invalid_mode")

    await _get_course(db, course_id)

    # random 模式不需要 mastery,直接随机
    words_stmt = select(Word).where(Word.course_id == course_id, Word.is_active.is_(True))
    if mode == "random":
        words_stmt = words_stmt.order_by(func.random()).limit(limit)
        result = await db.execute(words_stmt)
        return result.scalars().all()

    # 拉全部 active 词（按 sort_order）+ 用户的 mastery
    words = (
        await db.execute(
            words_stmt.order_by(Word.sort_order, Word.spelling)
        )
    ).scalars().all()
    if not words:
        return []

    lexeme_ids = [w.lexeme_id for w in words if w.lexeme_id is not None]
    mastery_map = await _load_mastery_map(db, user.id, lexeme_ids)

    def _mastery_of(w: Word) -> tuple[float, datetime | None]:
        """NULL lexeme_id 视为新词(mastery=0, last_assessed_at=None)。"""
        if w.lexeme_id is None:
            return (0.0, None)
        return mastery_map.get(w.lexeme_id, (0.0, None))

    if mode == "learn":
        # mastery 升序:未评估的(默认 0.0)优先
        learn_pool = sorted(words, key=lambda w: _mastery_of(w)[0])
        selected = learn_pool[:limit]
        random.shuffle(selected)
        return selected

    if mode == "review":
        review_pool = [w for w in words if _mastery_of(w)[0] >= 0.5]
        review_pool.sort(
            key=lambda w: _mastery_of(w)[1] or datetime.min.replace(tzinfo=timezone.utc)
        )
        return review_pool[:limit]

    if mode == "assess":
        # 分层抽样:4 个能力带各取约 1/4,不足从剩余池补齐(测评要覆盖全谱)
        def _band_of(w: Word) -> str:
            m, last_at = _mastery_of(w)
            if last_at is None:
                return "new"
            if m < 0.7:
                return "learning"
            if m < 0.9:
                return "familiar"
            return "mastered"

        quota = -(-limit // 4)  # ceil(limit/4)
        bands: dict[str, list[Word]] = {"new": [], "learning": [], "familiar": [], "mastered": []}
        for w in words:
            bands[_band_of(w)].append(w)
        selected: list[Word] = []
        for band in ("mastered", "familiar", "learning", "new"):
            pool = bands[band]
            random.shuffle(pool)
            selected.extend(pool[:quota])
        if len(selected) < limit:
            chosen = {w.id for w in selected}
            rest = [w for w in words if w.id not in chosen]
            random.shuffle(rest)
            selected.extend(rest[: limit - len(selected)])
        random.shuffle(selected)
        return selected[:limit]

    # adaptive: 70/30 切分
    learn_n = max(1, limit * 7 // 10)
    review_n = limit - learn_n
    learn_pool = sorted(words, key=lambda w: _mastery_of(w)[0])[:learn_n]
    review_pool = [w for w in words if _mastery_of(w)[0] >= 0.5]
    review_pool.sort(
        key=lambda w: _mastery_of(w)[1] or datetime.min.replace(tzinfo=timezone.utc)
    )
    selected = learn_pool + review_pool[:review_n]
    random.shuffle(selected)
    return selected


async def _load_mastery_map(
    db: AsyncSession, user_id: UUID, lexeme_ids: list[UUID]
) -> dict[UUID, tuple[float, datetime | None]]:
    """按 lexeme_id 查 mastery(v0.14.0 起全局共享)。"""
    if not lexeme_ids:
        return {}
    result = await db.execute(
        select(ChildWordMastery.lexeme_id, ChildWordMastery.mastery, ChildWordMastery.last_assessed_at)
        .where(ChildWordMastery.user_id == user_id, ChildWordMastery.lexeme_id.in_(lexeme_ids))
    )
    return {row[0]: (float(row[1]), row[2]) for row in result.all()}


@router.post("/{course_id}/words/{word_id}/assess", response_model=WordAssessmentResult)
async def assess_word_pronunciation(
    course_id: UUID,
    word_id: UUID,
    audio: bytes = File(...),
    ref_text_override: str | None = Form(default=None),
    category: str = Form(default="read_word"),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """评测用户朗读单词的发音（讯飞 ISE）。

    入参：
    - audio: 16kHz 16bit mono PCM raw bytes（multipart/form-data file 上传）
    - ref_text_override: 可选，前端可传参考文本（默认用 word.spelling）
    - category: 评测题型
        - read_word（默认）：单词朗读，参考文本为单个单词
        - read_sentence：句子/连读朗读，参考文本为整段（如 "D O G dog" 拼读+连读）

    返回：score (0-100) + passed (>= settings.reading_pass_score) + enabled (ISE 是否启用)

    ISE 未配置时返回 200 + enabled=false，前端降级为「固定等待」模式自动切下一个。
    ISE 启用且评分成功时,同步 upsert ChildWordMastery 更新能力模型。
    """
    # 校验课程 + 单词
    await _get_course(db, course_id)
    word_result = await db.execute(select(Word).where(Word.id == word_id, Word.course_id == course_id))
    word = word_result.scalar_one_or_none()
    if not word:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="word_not_found")

    ref_text = (ref_text_override or word.spelling).strip()
    if not ref_text:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="empty_ref_text")

    # ISE 未配置 → 200 + enabled=false（前端降级）
    if not all([settings.iflytek_app_id, settings.iflytek_api_key, settings.iflytek_api_secret]):
        return WordAssessmentResult(
            word_id=str(word.id),
            ref_text=ref_text,
            score=0,
            passed=False,
            enabled=False,
        )

    try:
        result = await iflytek_assess(
            audio_pcm=audio,
            ref_text=ref_text,
            app_id=settings.iflytek_app_id,
            api_key=settings.iflytek_api_key,
            api_secret=settings.iflytek_api_secret,
            category=category,
        )
    except IflytekIseError as e:
        logger.warning("ISE assess failed code=%s msg=%s", e.code, e.message)
        # 业务错误 → 502 让前端显示「评分失败」
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"ise_{e.code}",
        )

    # 同步更新能力模型（EMA upsert）
    # v0.14.0 起按 lexeme_id 记录;word.lexeme_id 为 NULL 时跳过(老数据未回填)
    if word.lexeme_id is not None:
        try:
            await upsert_mastery(
                db,
                user_id=user.id,
                lexeme_id=word.lexeme_id,
                family_id=user.family_id,
                score=result.score,
                pass_threshold=settings.reading_pass_score,
            )
        except Exception as e:
            # 能力模型更新失败不影响评分返回（评分已成功）,仅记日志
            logger.warning("upsert_mastery failed user=%s lexeme=%s err=%s", user.id, word.lexeme_id, e)

    return WordAssessmentResult(
        word_id=str(word.id),
        ref_text=ref_text,
        score=result.score,
        passed=result.score >= settings.reading_pass_score,
        enabled=True,
    )


@router.post("/{course_id}/words/{word_id}/score", response_model=WordScoreOut)
async def submit_word_score(
    course_id: UUID,
    word_id: UUID,
    body: WordScoreIn,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """学习/测评课的离线评分回写（客户端已完成拼写判对错）。

    与朗读的 ISE 评分共用 upsert_mastery（EMA + initial clamp），
    三个课程写入同一份全局 lexeme mastery。
    """
    await _get_course(db, course_id)
    word_result = await db.execute(
        select(Word).where(Word.id == word_id, Word.course_id == course_id)
    )
    word = word_result.scalar_one_or_none()
    if not word:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="word_not_found")
    if word.lexeme_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="word_not_linked")

    await upsert_mastery(
        db,
        user_id=user.id,
        lexeme_id=word.lexeme_id,
        family_id=user.family_id,
        score=body.score,
        pass_threshold=settings.reading_pass_score,
    )

    row = (
        await db.execute(
            select(ChildWordMastery).where(
                ChildWordMastery.user_id == user.id,
                ChildWordMastery.lexeme_id == word.lexeme_id,
            )
        )
    ).scalar_one()
    return WordScoreOut(
        word_id=word.id,
        lexeme_id=word.lexeme_id,
        mastery=float(row.mastery),
        attempts=row.attempts,
        passed_count=row.passed_count,
        best_score=row.best_score,
        last_score=row.last_score,
    )
