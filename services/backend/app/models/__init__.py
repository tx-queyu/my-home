"""模型聚合：Base + 所有表。"""
from app.models.appliance import Appliance, ApplianceStatus
from app.models.base import Base, TimestampMixin
from app.models.course import Course
from app.models.device import Device, DeviceCommand, DeviceCommandStatus, DeviceCommandType
from app.models.point import PointAccount, PointSource, PointTransaction
from app.models.reward import Redemption, RedemptionStatus, Reward
from app.models.self_study import SelfStudyTextbook
from app.models.skill import ChildWordMastery
from app.models.task import Task, TaskRecord
from app.models.user import Family, User, UserRole
from app.models.verification import EmailConfig, SmsConfig, VerificationCode
from app.models.word import Lexicon, Word

__all__ = [
    "Base",
    "TimestampMixin",
    "Family",
    "User",
    "UserRole",
    "Appliance",
    "ApplianceStatus",
    "Course",
    "Lexicon",
    "Word",
    "Task",
    "TaskRecord",
    "PointSource",
    "PointAccount",
    "PointTransaction",
    "Reward",
    "Redemption",
    "RedemptionStatus",
    "SelfStudyTextbook",
    "ChildWordMastery",
    "Device",
    "DeviceCommand",
    "DeviceCommandType",
    "DeviceCommandStatus",
    "SmsConfig",
    "EmailConfig",
    "VerificationCode",
]
