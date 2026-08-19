"""习惯打卡：家庭级习惯定义 + 每日打卡日志。"""
from datetime import date
from uuid import UUID, uuid4

from sqlalchemy import Boolean, Date, ForeignKey, Index, SmallInteger, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin


class Habit(Base, TimestampMixin):
    """习惯定义（家长为家庭建，如「早起」「阅读」）。"""

    __tablename__ = "habits"
    __table_args__ = (
        Index("ux_habits_family_name", "family_id", "name", unique=True),
        Index("ix_habits_family_id", "family_id"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    # 每连续天基础积分：当天积分 = min(streak_count, streak_cap) * points
    points: Mapped[int] = mapped_column(SmallInteger, default=1, nullable=False)
    # 连续天数封顶（防攒）：streak_count 超过 cap 后积分不再增长
    streak_cap: Mapped[int] = mapped_column(SmallInteger, default=7, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)


class HabitLog(Base, TimestampMixin):
    """打卡日志：每用户每习惯每天一条，streak_count 存当日连续天数（真实值，可超 cap）。"""

    __tablename__ = "habit_logs"
    __table_args__ = (
        # 照搬 task_records 按天唯一（app/models/task.py 同模式）
        Index("ux_habit_logs_habit_user_date", "habit_id", "user_id", "checkin_date", unique=True),
        Index("ix_habit_logs_user_id", "user_id"),
        Index("ix_habit_logs_checkin_date", "checkin_date"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    habit_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("habits.id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    streak_count: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    points_earned: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    checkin_date: Mapped[date] = mapped_column(
        Date, nullable=False, server_default=func.current_date()
    )
    # 冗余打卡当时的习惯名，习惯改名/删除后流水展示仍可读
    note: Mapped[str | None] = mapped_column(String(256), nullable=True)

    habit = relationship("Habit", lazy="joined")
    user = relationship("User", lazy="joined", foreign_keys=[user_id])

    @property
    def habit_name(self) -> str | None:
        return self.habit.name if self.habit is not None else None

    @property
    def username(self) -> str | None:
        return self.user.username if self.user is not None else None
