"""短信/邮件服务商配置 + 验证码。"""
from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import Boolean, DateTime, ForeignKey, Index, Integer, String, Text, Uuid, text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class SmsConfig(Base, TimestampMixin):
    """短信服务商配置 — multi-config，每行一个 provider，全局一行 is_active=true。

    v1 支持 aliyun/tencent/huawei 3 个 provider；AK/SK 用 Fernet 加密存（app/core/crypto.py）。
    一个 provider 只能建一条记录（ux_sms_configs_provider unique index）。
    """

    __tablename__ = "sms_configs"
    __table_args__ = (
        # 全局仅一行 is_active=true（PG 14+ partial unique index）
        Index(
            "ix_sms_configs_active",
            "is_active",
            unique=True,
            postgresql_where=text("is_active = TRUE"),
        ),
        Index("ux_sms_configs_provider", "provider", unique=True),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    provider: Mapped[str] = mapped_column(String(16), nullable=False)  # aliyun / tencent / huawei
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False, server_default=text("false"))

    # 共享字段（按 provider 在 schema 层校验必填；DB 层 nullable 便于 multi-config 切换）
    sign_name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    template_code: Mapped[str | None] = mapped_column(String(64), nullable=True)

    # 云厂商通用 AK/SK
    access_key_id_encrypted: Mapped[str | None] = mapped_column(Text, nullable=True)
    access_key_secret_encrypted: Mapped[str | None] = mapped_column(Text, nullable=True)

    # provider 特定字段
    sdk_app_id: Mapped[str | None] = mapped_column(String(128), nullable=True)  # 腾讯云 SmsSdkAppId
    region: Mapped[str | None] = mapped_column(String(64), nullable=True)

    # 风控参数
    daily_limit: Mapped[int] = mapped_column(Integer, nullable=False, default=1000, server_default=text("1000"))
    interval_seconds: Mapped[int] = mapped_column(Integer, nullable=False, default=60, server_default=text("60"))

    created_by: Mapped[UUID] = mapped_column(Uuid, ForeignKey("users.id"), nullable=False)


class EmailConfig(Base, TimestampMixin):
    """邮件服务商配置 — multi-config，全局一行 is_active=true。

    v1 支持 smtp + aliyun/tencent/huawei 4 个 provider。SMTP 密码 / 云厂商 AK/SK 用 Fernet 加密存。
    """

    __tablename__ = "email_configs"
    __table_args__ = (
        Index(
            "ix_email_configs_active",
            "is_active",
            unique=True,
            postgresql_where=text("is_active = TRUE"),
        ),
        Index("ux_email_configs_provider", "provider", unique=True),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    provider: Mapped[str] = mapped_column(String(16), nullable=False)  # smtp / aliyun / tencent / huawei
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False, server_default=text("false"))

    # SMTP 特定字段
    smtp_host: Mapped[str | None] = mapped_column(String(255), nullable=True)
    smtp_port: Mapped[int | None] = mapped_column(Integer, nullable=True, default=465, server_default=text("465"))
    encryption: Mapped[str | None] = mapped_column(String(16), nullable=True, default="ssl", server_default=text("'ssl'"))
    username: Mapped[str | None] = mapped_column(String(255), nullable=True)
    password_encrypted: Mapped[str | None] = mapped_column(Text, nullable=True)

    # 云厂商通用字段
    access_key_id_encrypted: Mapped[str | None] = mapped_column(Text, nullable=True)
    access_key_secret_encrypted: Mapped[str | None] = mapped_column(Text, nullable=True)
    region: Mapped[str | None] = mapped_column(String(64), nullable=True)
    from_email: Mapped[str | None] = mapped_column(String(255), nullable=True)

    # 共享字段
    from_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    daily_limit: Mapped[int] = mapped_column(Integer, nullable=False, default=200, server_default=text("200"))
    interval_seconds: Mapped[int] = mapped_column(Integer, nullable=False, default=60, server_default=text("60"))

    created_by: Mapped[UUID] = mapped_column(Uuid, ForeignKey("users.id"), nullable=False)


class VerificationCode(Base):
    """验证码 — 6 位数字 OTP，bcrypt hash 存，10min 有效，5 次错误失效。

    verify 成功后 consumed_at 标记，不可重放（不签 VerificationTicket，直接签 verify_token JWT）。
    """

    __tablename__ = "verification_codes"
    __table_args__ = (
        Index("ix_verification_codes_lookup", "target", "purpose", "created_at"),
    )

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    channel: Mapped[str] = mapped_column(String(8), nullable=False)  # sms / email
    target: Mapped[str] = mapped_column(String(256), nullable=False)  # 手机号 or 邮箱
    purpose: Mapped[str] = mapped_column(String(32), nullable=False)
    # register / reset_password / login_by_code / change_phone / change_email
    code_hash: Mapped[str] = mapped_column(String(256), nullable=False)  # bcrypt hash
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    consumed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    attempt_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default=text("0"))
    ip: Mapped[str | None] = mapped_column(String(64), nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=text("NOW()"),
        nullable=False,
    )
