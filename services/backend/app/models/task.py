"""任务/作业 + 完成记录。"""
from datetime import date, time
from uuid import UUID, uuid4

from sqlalchemy import ARRAY, Boolean, Date, ForeignKey, Index, Integer, SmallInteger, String, Text, Time, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin


class Task(Base, TimestampMixin):
    __tablename__ = "tasks"
    __table_args__ = (Index("ix_tasks_family_id", "family_id"),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=False
    )
    course_id: Mapped[UUID | None] = mapped_column(
        Uuid, ForeignKey("courses.id", ondelete="SET NULL"), nullable=True
    )
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    points: Mapped[int] = mapped_column(SmallInteger, default=1, nullable=False)
    due_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)

    # v0.12.0: 指派孩子（null = 家庭内任意孩子可完成）
    assignee_user_id: Mapped[UUID | None] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    # 可完成日期范围（null = 不限）
    available_start_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    available_end_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    # 每日时间窗口（null = 全天）
    available_start_time: Mapped[time | None] = mapped_column(Time, nullable=True)
    available_end_time: Mapped[time | None] = mapped_column(Time, nullable=True)
    # 周期：one_off | daily | weekly
    recurrence_type: Mapped[str] = mapped_column(
        String(16), nullable=False, server_default="one_off"
    )
    # weekly 时生效：1=周一 ... 7=周日
    recurrence_weekdays: Mapped[list[int] | None] = mapped_column(ARRAY(Integer), nullable=True)

    course = relationship("Course", lazy="joined")
    assignee = relationship("User", lazy="joined", foreign_keys=[assignee_user_id])

    @property
    def assignee_username(self) -> str | None:
        return self.assignee.username if self.assignee is not None else None


class TaskRecord(Base, TimestampMixin):
    __tablename__ = "task_records"
    __table_args__ = (
        Index("ux_task_records_task_user_date", "task_id", "user_id", "completed_date", unique=True),
        Index("ix_task_records_user_id", "user_id"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    task_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    points_earned: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    # v0.12.0: 完成日期（周期任务按天查重）
    completed_date: Mapped[date] = mapped_column(
        Date, nullable=False, server_default=func.current_date()
    )
