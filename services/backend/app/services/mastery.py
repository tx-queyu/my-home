"""能力模型更新 —— EMA 滑动平均 upsert。

调用点:`api/courses.py:assess_word_pronunciation` 在 ISE 评分成功后调用。
ISE 未配置(降级模式)时不调用本函数,避免污染能力数据。
"""
from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import func
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import ChildWordMastery


async def upsert_mastery(
    db: AsyncSession,
    user_id: UUID,
    lexeme_id: UUID,
    family_id: UUID | None,
    score: int,
    pass_threshold: int = 60,
) -> None:
    """EMA 更新 mastery。

    新词:初值 = clamp(score/100, 0.1, 0.5),避免一次满分直接到 1.0。
    已有记录:mastery = mastery * 0.7 + (score/100) * 0.3,alpha=0.3 缓冲突发发挥。

    使用 PostgreSQL 原生 `on_conflict_do_update` 原子 upsert,无并发问题。
    v0.14.0 起按 lexeme_id 全局共享——同一个词在多个课程学到的 mastery 累计到同一条记录。
    """
    now = datetime.now(timezone.utc)
    initial = min(0.5, max(0.1, score / 100))
    passed_inc = 1 if score >= pass_threshold else 0

    stmt = (
        insert(ChildWordMastery)
        .values(
            user_id=user_id,
            lexeme_id=lexeme_id,
            family_id=family_id,
            mastery=initial,
            attempts=1,
            passed_count=passed_inc,
            best_score=score,
            last_score=score,
            last_assessed_at=now,
        )
        .on_conflict_do_update(
            index_elements=["user_id", "lexeme_id"],
            set_={
                "mastery": ChildWordMastery.mastery * 0.7 + (score / 100.0) * 0.3,
                "attempts": ChildWordMastery.attempts + 1,
                "passed_count": ChildWordMastery.passed_count + passed_inc,
                "best_score": func.greatest(ChildWordMastery.best_score, score),
                "last_score": score,
                "last_assessed_at": now,
                "updated_at": now,
            },
        )
    )
    await db.execute(stmt)
    await db.commit()
