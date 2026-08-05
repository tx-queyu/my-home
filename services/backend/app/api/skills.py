"""能力模型 API —— 单词维度（v0.14.0 全局化，v0.16.2 教材维度）。

6 个端点:
- GET /api/skills/me                                孩子查自己全局概览(仅接触过的词统计)
- GET /api/skills/me/textbooks                      查自己教材覆盖(v0.16.2 起按教材聚合)
- GET /api/skills/me/words?state=&subject=&textbook=  查自己单词明细(可按教材过滤)
- GET /api/skills/children/{cid}                    家长查孩子全局概览
- GET /api/skills/children/{cid}/textbooks          家长查孩子教材覆盖
- GET /api/skills/children/{cid}/words?state=&subject=&textbook=  家长查孩子单词明细

v0.16.2 起:
- 覆盖度从「课程维度」改为「教材维度」:教材 = (subject, textbook),
  其下各课程(朗读/学习/测评)共享同一批 lexeme,教材维度天然去重,
  课程只是教材的学习手段
- 孩子的教材集合 = 当前任务涉及的课程所属教材;家长(非 child 角色)
  = 我的教材 ∪ 接触过的课程所属教材
- 单词明细过滤从 course_id 改为 subject+textbook(两者须同时提供)
- 跨家庭访问返回 404 child_not_found(不暴露存在性)
"""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, or_, select, tuple_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, require_parent
from app.models import (
    ChildWordMastery,
    Course,
    Lexicon,
    SelfStudyTextbook,
    Task,
    User,
    UserRole,
    Word,
)
from app.schemas.skill import ChildWordMasteryOut, SkillOverviewOut, TextbookCoverageOut

router = APIRouter(prefix="/api/skills", tags=["skills"])

VALID_STATES = {"new", "learning", "familiar", "mastered"}
MASTERED_THRESHOLD = 0.9
REVIEW_THRESHOLD = 0.5


def _mastery_to_state(mastery: float | int) -> str:
    """0-1 mastery → 状态分类(仅处理已评估词)。未评估词在聚合时单独识别为 "new"。"""
    m = float(mastery)
    if m < 0.7:
        return "learning"
    if m < 0.9:
        return "familiar"
    return "mastered"


async def _get_family_child(
    db: AsyncSession, child_id: UUID, family_id: UUID | None
) -> User | None:
    """跨家庭校验:返回家庭内的孩子 User,跨家庭/不存在返回 None。"""
    if family_id is None:
        return None
    result = await db.execute(
        select(User).where(
            User.id == child_id,
            User.family_id == family_id,
            User.is_active.is_(True),
        )
    )
    return result.scalar_one_or_none()


async def _build_overview(db: AsyncSession, user_id: UUID) -> SkillOverviewOut:
    """全局能力概览:跨所有课程的单词掌握度。"""
    # 1. 全局 active lexeme 总数(distinct lexeme_id from active words)
    total_lexemes = (
        await db.execute(
            select(func.count(func.distinct(Word.lexeme_id))).where(
                Word.is_active.is_(True), Word.lexeme_id.isnot(None)
            )
        )
    ).scalar() or 0

    # 2. 该孩子所有 mastery 记录
    mastery_rows = (
        await db.execute(
            select(ChildWordMastery).where(ChildWordMastery.user_id == user_id)
        )
    ).scalars().all()

    # 3. 状态分类
    by_state = {"new": 0, "learning": 0, "familiar": 0, "mastered": 0}
    mastery_sum = 0.0
    for m in mastery_rows:
        state = _mastery_to_state(m.mastery)
        by_state[state] += 1
        mastery_sum += float(m.mastery)

    assessed = len(mastery_rows)
    mastered = by_state["mastered"]
    by_state["new"] = max(0, total_lexemes - assessed)  # 全局词中还没碰过的
    avg = mastery_sum / assessed if assessed > 0 else 0.0

    return SkillOverviewOut(
        total_words=total_lexemes,
        assessed_words=assessed,
        mastered_words=mastered,
        average_mastery=round(avg, 3),
        coverage=round(assessed / total_lexemes, 3) if total_lexemes > 0 else 0.0,
        mastered_coverage=round(mastered / total_lexemes, 3) if total_lexemes > 0 else 0.0,
        by_state=by_state,
    )


async def _build_textbook_coverage(
    db: AsyncSession, user_id: UUID, family_id: UUID | None, touched_only: bool = False
) -> list[TextbookCoverageOut]:
    """能力映射到教材:每个教材的总词数 / 接触过 / 已掌握(v0.16.2 教材维度)。

    教材集合两种取法:
    - touched_only=False(孩子):「当前任务涉及的课程」所属教材——家庭 active
      任务中 course_id 非空、且指派给该孩子或未指派的任务所引用的课程,
      取这些课程的 distinct (subject, textbook)。
    - touched_only=True(家长自学):「我的教材 ∪ 接触过的课程所属教材」——
      我的教材来自 self_study_textbooks(添加即出现,未学显示 0 进度);
      接触过的 = 存在 active 词的 lexeme 在该用户 mastery 里的课程所属教材。
    """
    if touched_only:
        textbook_pairs: set[tuple[str, str]] = set(
            (
                await db.execute(
                    select(SelfStudyTextbook.subject, SelfStudyTextbook.textbook).where(
                        SelfStudyTextbook.user_id == user_id
                    )
                )
            ).all()
        )
        touched_pairs = (
            await db.execute(
                select(Course.subject, Course.textbook).where(
                    Course.is_active.is_(True),
                    Course.id.in_(
                        select(func.distinct(Word.course_id)).where(
                            Word.is_active.is_(True),
                            Word.lexeme_id.in_(
                                select(ChildWordMastery.lexeme_id).where(
                                    ChildWordMastery.user_id == user_id
                                )
                            ),
                        )
                    ),
                )
            )
        ).all()
        textbook_pairs |= set(touched_pairs)
    else:
        if family_id is None:
            return []
        course_ids = (
            await db.execute(
                select(func.distinct(Task.course_id)).where(
                    Task.family_id == family_id,
                    Task.is_active.is_(True),
                    Task.course_id.isnot(None),
                    or_(
                        Task.assignee_user_id == user_id,
                        Task.assignee_user_id.is_(None),
                    ),
                )
            )
        ).scalars().all()
        if not course_ids:
            return []
        textbook_pairs = set(
            (
                await db.execute(
                    select(Course.subject, Course.textbook).where(
                        Course.id.in_(course_ids), Course.is_active.is_(True)
                    )
                )
            ).all()
        )
    if not textbook_pairs:
        return []

    # 这些教材下的全部 active 课程(按 sort_order 排,learning_methods 有序)
    course_rows = (
        await db.execute(
            select(Course)
            .where(
                Course.is_active.is_(True),
                tuple_(Course.subject, Course.textbook).in_(list(textbook_pairs)),
            )
            .order_by(Course.subject, Course.textbook, Course.sort_order)
        )
    ).scalars().all()
    if not course_rows:
        return []

    # 该用户的所有 mastery 按 lexeme_id 索引
    mastery_rows = (
        await db.execute(
            select(ChildWordMastery).where(ChildWordMastery.user_id == user_id)
        )
    ).scalars().all()
    mastery_map: dict[UUID, float] = {m.lexeme_id: float(m.mastery) for m in mastery_rows}

    courses_by_textbook: dict[tuple[str, str], list[Course]] = {}
    for c in course_rows:
        courses_by_textbook.setdefault((c.subject, c.textbook), []).append(c)

    result: list[TextbookCoverageOut] = []
    for (subject, textbook), courses in courses_by_textbook.items():
        # 该教材全部 active 课程的 distinct lexeme_id(共享 lexeme 天然去重)
        lexeme_ids = (
            await db.execute(
                select(func.distinct(Word.lexeme_id)).where(
                    Word.course_id.in_([c.id for c in courses]),
                    Word.is_active.is_(True),
                    Word.lexeme_id.isnot(None),
                )
            )
        ).scalars().all()

        total = len(lexeme_ids)
        touched = sum(1 for lid in lexeme_ids if lid in mastery_map)
        mastered = sum(1 for lid in lexeme_ids if mastery_map.get(lid, 0.0) >= MASTERED_THRESHOLD)

        result.append(
            TextbookCoverageOut(
                subject=subject,
                textbook=textbook,
                learning_methods=[c.learning_method for c in courses],
                total_words=total,
                touched_words=touched,
                mastered_words=mastered,
                touched_coverage=round(touched / total, 3) if total > 0 else 0.0,
                mastered_coverage=round(mastered / total, 3) if total > 0 else 0.0,
                is_completed=(mastered == total and total > 0),
            )
        )
    return result


async def _list_word_mastery(
    db: AsyncSession,
    user_id: UUID,
    state: str | None,
    subject: str | None = None,
    textbook: str | None = None,
) -> list[ChildWordMasteryOut]:
    """单词 mastery 明细(全局 lexeme 维度,可按教材过滤)。

    subject+textbook 同时提供时只返回该教材(全部 active 课程)对应的 lexeme;
    state=new 返回「未评估」的 lexeme(用 0 占位);
    其它状态过滤已评估的 mastery 记录。
    """
    # active words 的 lexeme_id + 对应 lexicon 字段(可按教材收窄)
    stmt = (
        select(Lexicon)
        .join(Word, Word.lexeme_id == Lexicon.id)
        .where(Word.is_active.is_(True))
    )
    if subject is not None and textbook is not None:
        stmt = stmt.join(Course, Course.id == Word.course_id).where(
            Course.subject == subject, Course.textbook == textbook
        )
    lexeme_rows = (await db.execute(stmt.distinct())).scalars().all()
    if not lexeme_rows:
        return []

    # 孩子的 mastery map
    mastery_rows = (
        await db.execute(
            select(ChildWordMastery).where(ChildWordMastery.user_id == user_id)
        )
    ).scalars().all()
    mastery_map: dict[UUID, ChildWordMastery] = {m.lexeme_id: m for m in mastery_rows}

    out: list[ChildWordMasteryOut] = []
    for lex in lexeme_rows:
        m = mastery_map.get(lex.id)
        lex_state = "new" if m is None else _mastery_to_state(m.mastery)
        if state is not None and lex_state != state:
            continue
        out.append(
            ChildWordMasteryOut(
                lexeme_id=lex.id,
                spelling=lex.spelling,
                meaning_cn=lex.meaning_cn,
                phonetic=lex.phonetic,
                mastery=float(m.mastery) if m else 0.0,
                attempts=m.attempts if m else 0,
                passed_count=m.passed_count if m else 0,
                best_score=m.best_score if m else 0,
                last_score=m.last_score if m else None,
                last_assessed_at=m.last_assessed_at if m else None,
                state=lex_state,
            )
        )

    # 按状态 + spelling 排序(new 在最后,便于家长看已评估词)
    state_order = {"mastered": 0, "familiar": 1, "learning": 2, "new": 3}
    out.sort(key=lambda x: (state_order.get(x.state, 99), x.spelling))
    return out


# ============================================================
# 孩子视角(自己)
# ============================================================

@router.get("/me", response_model=SkillOverviewOut)
async def get_my_skill_overview(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """孩子查自己的全局单词能力概览。"""
    return await _build_overview(db, user.id)


@router.get("/me/textbooks", response_model=list[TextbookCoverageOut])
async def list_my_textbook_coverage(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """查自己的教材覆盖度(v0.16.2 教材维度)。

    孩子(roles 含 child):「当前任务涉及的课程」所属教材;
    家长(非 child 角色):「我的教材 ∪ 接触过的课程所属教材」——
    自学产生的 mastery 映射回教材,供家长自学能力中心使用。
    """
    touched_only = UserRole.child not in user.roles
    return await _build_textbook_coverage(
        db, user.id, user.family_id, touched_only=touched_only
    )


@router.get("/me/words", response_model=list[ChildWordMasteryOut])
async def list_my_word_mastery(
    state: str | None = Query(None, max_length=16),
    subject: str | None = Query(None, max_length=32),
    textbook: str | None = Query(None, max_length=64),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """孩子查自己的单词 mastery 明细(可按教材过滤,subject+textbook 须同时提供)。"""
    if state is not None and state not in VALID_STATES:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="invalid_state")
    if (subject is None) != (textbook is None):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="invalid_textbook_filter"
        )
    return await _list_word_mastery(db, user.id, state, subject, textbook)


# ============================================================
# 家长视角(看孩子,跨家庭校验)
# ============================================================

@router.get("/children/{child_id}", response_model=SkillOverviewOut)
async def get_child_skill_overview(
    child_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    """家长查指定孩子的全局能力概览。"""
    target = await _get_family_child(db, child_id, parent.family_id)
    if target is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="child_not_found")
    return await _build_overview(db, child_id)


@router.get("/children/{child_id}/textbooks", response_model=list[TextbookCoverageOut])
async def list_child_textbook_coverage(
    child_id: UUID,
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    """家长查指定孩子的教材覆盖度(仅当前任务涉及的课程所属教材)。"""
    target = await _get_family_child(db, child_id, parent.family_id)
    if target is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="child_not_found")
    return await _build_textbook_coverage(db, child_id, parent.family_id)


@router.get("/children/{child_id}/words", response_model=list[ChildWordMasteryOut])
async def list_child_word_mastery(
    child_id: UUID,
    state: str | None = Query(None, max_length=16),
    subject: str | None = Query(None, max_length=32),
    textbook: str | None = Query(None, max_length=64),
    parent: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
):
    """家长查指定孩子的单词 mastery 明细(可按教材过滤)。"""
    target = await _get_family_child(db, child_id, parent.family_id)
    if target is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="child_not_found")
    if state is not None and state not in VALID_STATES:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="invalid_state")
    if (subject is None) != (textbook is None):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="invalid_textbook_filter"
        )
    return await _list_word_mastery(db, child_id, state, subject, textbook)
