"""任务/作业相关 Pydantic 模型。"""
from datetime import date, datetime, time
from uuid import UUID

from pydantic import BaseModel, Field, model_validator

from app.schemas.course import CourseOut

RECURRENCE_TYPES = ("one_off", "daily", "weekly")


class TaskBase(BaseModel):
    title: str = Field(min_length=1, max_length=128)
    description: str | None = Field(default=None, max_length=4000)
    course_id: UUID | None = None
    points: int = Field(default=1, ge=1, le=1000)
    due_date: date | None = None
    is_active: bool = True
    assignee_user_id: UUID | None = None
    available_start_date: date | None = None
    available_end_date: date | None = None
    available_start_time: time | None = None
    available_end_time: time | None = None
    recurrence_type: str = Field(default="one_off", pattern="^(one_off|daily|weekly)$")
    recurrence_weekdays: list[int] | None = None

    @model_validator(mode="after")
    def _check_availability_rules(self):
        if self.recurrence_type == "weekly":
            if not self.recurrence_weekdays:
                raise ValueError("weekly_recurrence_requires_weekdays")
            if any(d < 1 or d > 7 for d in self.recurrence_weekdays):
                raise ValueError("weekday_out_of_range")
        if (
            self.available_start_date
            and self.available_end_date
            and self.available_end_date < self.available_start_date
        ):
            raise ValueError("available_end_before_start")
        if (
            self.available_start_time
            and self.available_end_time
            and self.available_end_time <= self.available_start_time
        ):
            raise ValueError("time_window_end_before_start")
        return self


class TaskCreate(TaskBase):
    pass


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=128)
    description: str | None = Field(default=None, max_length=4000)
    course_id: UUID | None = None
    points: int | None = Field(default=None, ge=1, le=1000)
    due_date: date | None = None
    is_active: bool | None = None
    assignee_user_id: UUID | None = None
    available_start_date: date | None = None
    available_end_date: date | None = None
    available_start_time: time | None = None
    available_end_time: time | None = None
    recurrence_type: str | None = Field(default=None, pattern="^(one_off|daily|weekly)$")
    recurrence_weekdays: list[int] | None = None


class TaskOut(TaskBase):
    id: UUID
    family_id: UUID
    course: CourseOut | None = None
    assignee_username: str | None = None
    completed_today: bool = False
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class TaskRecordOut(BaseModel):
    id: UUID
    task_id: UUID
    user_id: UUID
    points_earned: int
    completed_date: date
    created_at: datetime

    model_config = {"from_attributes": True}


class TaskWithRecordOut(TaskOut):
    """任务详情 + 当前用户是否已完成。"""
    completed: bool = False
    points_earned: int | None = None
