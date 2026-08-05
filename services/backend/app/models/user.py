"""家庭与用户。"""
import enum
from uuid import UUID, uuid4

from sqlalchemy import ARRAY, Boolean, Enum as SAEnum
from sqlalchemy import ForeignKey, Index, String, Uuid, text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class UserRole(str, enum.Enum):
    parent = "parent"
    child = "child"
    family_admin = "family_admin"
    admin = "admin"


class Family(Base, TimestampMixin):
    __tablename__ = "families"

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(128), nullable=False)


class User(Base, TimestampMixin):
    __tablename__ = "users"
    __table_args__ = (
        Index("ux_users_username", "username", unique=True),
        Index("ix_users_family_id", "family_id"),
        # 同一手机号仅可绑定一个已验证账号
        Index(
            "ux_users_phone",
            "phone",
            unique=True,
            postgresql_where=text("phone IS NOT NULL AND phone_verified = TRUE"),
        ),
        Index(
            "ux_users_email",
            "email",
            unique=True,
            postgresql_where=text("email IS NOT NULL AND email_verified = TRUE"),
        ),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    family_id: Mapped[UUID | None] = mapped_column(
        Uuid, ForeignKey("families.id", ondelete="CASCADE"), nullable=True
    )
    username: Mapped[str] = mapped_column(String(64), nullable=False)
    password_hash: Mapped[str] = mapped_column(String(256), nullable=False)
    display_name: Mapped[str] = mapped_column(String(64), nullable=False)
    roles: Mapped[list[UserRole]] = mapped_column(
        ARRAY(SAEnum(UserRole, name="user_role", native_enum=True)),
        nullable=False,
        default=list,
        server_default=text("ARRAY[]::user_role[]"),
    )
    is_active: Mapped[bool] = mapped_column(default=True, nullable=False)
    # 联系方式（注册时通过验证码验证后写入，phone_verified/email_verified=True）
    phone: Mapped[str | None] = mapped_column(String(32), nullable=True)
    email: Mapped[str | None] = mapped_column(String(255), nullable=True)
    phone_verified: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
    email_verified: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
