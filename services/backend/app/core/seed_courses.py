"""系统预置课程种子数据。

2 层结构：subject（学科）→ textbook（教材）→ learning_method（学习方式）。

英语：27 教材 × 9 方式 = 243 条（小学人教版 1-6 年级上下 12 + 初中人教版 7-9 年级上下 6 +
高中人教版高一-高三上下 6 + KET/托业/雅思 3）。

其他 12 学科保留旧 4 条左右课程作为占位（textbook="默认"），待后续按 2 层结构重新设计。
"""
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.course import Course

# 9 种学习方式（跨学科统一），(name, default_points)
LEARNING_METHODS: list[tuple[str, int]] = [
    ("朗读", 5),
    ("学习", 5),
    ("测评", 15),
    ("背诵", 5),
    ("听力", 5),
    ("口语", 10),
    ("写作", 10),
    ("授课", 10),
    ("测试", 15),
]

# 英语教材（27 个，按难度递增）
ENGLISH_TEXTBOOKS: list[str] = [
    "小学人教版一年级上", "小学人教版一年级下",
    "小学人教版二年级上", "小学人教版二年级下",
    "小学人教版三年级上", "小学人教版三年级下",
    "小学人教版四年级上", "小学人教版四年级下",
    "小学人教版五年级上", "小学人教版五年级下",
    "小学人教版六年级上", "小学人教版六年级下",
    "初中人教版七年级上", "初中人教版七年级下",
    "初中人教版八年级上", "初中人教版八年级下",
    "初中人教版九年级上", "初中人教版九年级下",
    "高中人教版高一上", "高中人教版高一下",
    "高中人教版高二上", "高中人教版高二下",
    "高中人教版高三上", "高中人教版高三下",
    "KET", "托业", "雅思",
]

# 其他 12 学科占位课程（subject, learning_method, default_points）—— textbook="默认"，
# 待后续按 2 层结构重新设计后替换。
LEGACY_PLACEHOLDER_COURSES: list[tuple[str, str, int]] = [
    # 数学
    ("数学", "完成20道口算题", 10),
    ("数学", "做一张数学试卷", 20),
    ("数学", "看15分钟数学教学视频", 5),
    ("数学", "做5道应用题", 8),
    # 语文
    ("语文", "背诵古诗一首", 8),
    ("语文", "阅读30分钟课外书", 10),
    ("语文", "写一篇日记", 15),
    ("语文", "抄写生字词20个", 5),
    ("语文", "完成一篇阅读理解", 10),
    # 物理
    ("物理", "做10道物理题", 10),
    ("物理", "做一个物理实验", 15),
    ("物理", "看物理教学视频15分钟", 5),
    # 化学
    ("化学", "做10道化学题", 10),
    ("化学", "做一个化学实验", 15),
    # 生物
    ("生物", "做10道生物题", 10),
    ("生物", "看生物纪录片15分钟", 5),
    # 历史
    ("历史", "做历史练习题", 8),
    ("历史", "读历史读物30分钟", 10),
    ("历史", "看历史纪录片15分钟", 5),
    # 地理
    ("地理", "做地理练习题", 8),
    ("地理", "看地理纪录片15分钟", 5),
    ("地理", "读地理读物30分钟", 10),
    # 体育
    ("体育", "跑步30分钟", 15),
    ("体育", "跳绳200个", 10),
    ("体育", "做50个仰卧起坐", 10),
    ("体育", "做5分钟拉伸", 5),
    # 音乐
    ("音乐", "练习乐器30分钟", 15),
    ("音乐", "唱一首歌", 10),
    ("音乐", "听古典音乐15分钟", 5),
    # 美术
    ("美术", "绘画30分钟", 10),
    ("美术", "练习书法30分钟", 10),
    ("美术", "完成一幅水彩画", 15),
    # 课外
    ("课外", "阅读课外书30分钟", 10),
    ("课外", "写一篇读书笔记", 15),
    # 实践
    ("实践", "做家务", 10),
    ("实践", "烹饪一道菜", 15),
    ("实践", "整理房间", 10),
]


def _build_seed_courses() -> list[tuple[str, str, str, str | None, int, int]]:
    """生成 SEED_COURSES：(subject, textbook, learning_method, description, default_points, sort_order)."""
    rows: list[tuple[str, str, str, str | None, int, int]] = []
    sort = 1
    # 英语 189 条
    for textbook in ENGLISH_TEXTBOOKS:
        for method, points in LEARNING_METHODS:
            rows.append(("英语", textbook, method, None, points, sort))
            sort += 1
    # 其他 12 学科占位（按学科分组，每学科内 sort_order 从 1 重计）
    legacy_by_subject: dict[str, list[tuple[str, int]]] = {}
    for subject, method, points in LEGACY_PLACEHOLDER_COURSES:
        legacy_by_subject.setdefault(subject, []).append((method, points))
    for subject, items in legacy_by_subject.items():
        for idx, (method, points) in enumerate(items, start=1):
            rows.append((subject, "默认", method, None, points, idx))
    return rows


SEED_COURSES: list[tuple[str, str, str, str | None, int, int]] = _build_seed_courses()


async def seed_courses_if_empty(db: AsyncSession) -> int:
    """若 courses 表为空，写入 SEED_COURSES。返回写入条数。"""
    result = await db.execute(select(Course).limit(1))
    if result.scalar_one_or_none() is not None:
        return 0
    db.add_all([
        Course(
            subject=subject,
            textbook=textbook,
            learning_method=learning_method,
            description=description,
            default_points=default_points,
            sort_order=sort_order,
        )
        for subject, textbook, learning_method, description, default_points, sort_order in SEED_COURSES
    ])
    await db.commit()
    return len(SEED_COURSES)
