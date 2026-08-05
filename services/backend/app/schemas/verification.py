"""验证码相关 Pydantic 模型 — SmsConfig / EmailConfig / VerificationCode。"""
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field, model_validator


# ---- SmsConfig ----
class SmsConfigBase(BaseModel):
    provider: Literal["aliyun", "tencent", "huawei"]
    sign_name: str | None = Field(default=None, max_length=64)
    template_code: str | None = Field(default=None, max_length=64)
    access_key_id: str | None = Field(default=None, max_length=256)
    access_key_secret: str | None = Field(default=None, max_length=256)
    sdk_app_id: str | None = Field(default=None, max_length=128)  # 腾讯云专用
    region: str | None = Field(default=None, max_length=64)
    daily_limit: int = Field(default=1000, ge=1, le=100000)
    interval_seconds: int = Field(default=60, ge=10, le=3600)


class SmsConfigCreate(SmsConfigBase):
    @model_validator(mode="after")
    def _validate_required(self):
        # AK/SK 必填
        if not self.access_key_id or not self.access_key_secret:
            raise ValueError("access_key_id / access_key_secret 必填")
        # 共享字段必填
        if not self.sign_name:
            raise ValueError("sign_name 必填")
        if not self.template_code:
            raise ValueError("template_code 必填")
        # 腾讯云 sdk_app_id 必填
        if self.provider == "tencent" and not self.sdk_app_id:
            raise ValueError("sdk_app_id 必填（腾讯云）")
        return self


class SmsConfigUpdate(BaseModel):
    sign_name: str | None = Field(default=None, max_length=64)
    template_code: str | None = Field(default=None, max_length=64)
    access_key_id: str | None = Field(default=None, max_length=256)
    access_key_secret: str | None = Field(default=None, max_length=256)
    sdk_app_id: str | None = Field(default=None, max_length=128)
    region: str | None = Field(default=None, max_length=64)
    daily_limit: int | None = Field(default=None, ge=1, le=100000)
    interval_seconds: int | None = Field(default=None, ge=10, le=3600)


class SmsConfigResponse(BaseModel):
    id: UUID
    provider: str
    is_active: bool
    sign_name: str | None
    template_code: str | None
    sdk_app_id: str | None
    region: str | None
    access_key_id_configured: bool  # 仅返回是否已配置，不返回明文
    daily_limit: int
    interval_seconds: int
    created_at: str

    model_config = {"from_attributes": False}


# ---- EmailConfig ----
class EmailConfigBase(BaseModel):
    provider: Literal["smtp", "aliyun", "tencent", "huawei"]
    # SMTP 字段
    smtp_host: str | None = Field(default=None, max_length=255)
    smtp_port: int | None = Field(default=None, ge=1, le=65535)
    encryption: Literal["ssl", "starttls", "none"] | None = "ssl"
    username: str | None = Field(default=None, max_length=255)
    password: str | None = Field(default=None, max_length=256)  # 明文入参，落库前 Fernet 加密
    # 云厂商字段
    access_key_id: str | None = Field(default=None, max_length=256)
    access_key_secret: str | None = Field(default=None, max_length=256)
    region: str | None = Field(default=None, max_length=64)
    from_email: str | None = Field(default=None, max_length=255)
    from_name: str | None = Field(default=None, max_length=128)
    daily_limit: int = Field(default=200, ge=1, le=100000)
    interval_seconds: int = Field(default=60, ge=10, le=3600)


class EmailConfigCreate(EmailConfigBase):
    @model_validator(mode="after")
    def _validate_required(self):
        if not self.from_email:
            raise ValueError("from_email 必填")
        if self.provider == "smtp":
            if not self.smtp_host:
                raise ValueError("smtp_host 必填（smtp provider）")
            if not self.username:
                raise ValueError("username 必填（smtp provider）")
            if not self.password:
                raise ValueError("password 必填（smtp provider）")
        else:
            if not self.access_key_id or not self.access_key_secret:
                raise ValueError("access_key_id / access_key_secret 必填（云厂商）")
        return self


class EmailConfigUpdate(BaseModel):
    smtp_host: str | None = Field(default=None, max_length=255)
    smtp_port: int | None = Field(default=None, ge=1, le=65535)
    encryption: Literal["ssl", "starttls", "none"] | None = None
    username: str | None = Field(default=None, max_length=255)
    password: str | None = Field(default=None, max_length=256)
    access_key_id: str | None = Field(default=None, max_length=256)
    access_key_secret: str | None = Field(default=None, max_length=256)
    region: str | None = Field(default=None, max_length=64)
    from_email: str | None = Field(default=None, max_length=255)
    from_name: str | None = Field(default=None, max_length=128)
    daily_limit: int | None = Field(default=None, ge=1, le=100000)
    interval_seconds: int | None = Field(default=None, ge=10, le=3600)


class EmailConfigResponse(BaseModel):
    id: UUID
    provider: str
    is_active: bool
    smtp_host: str | None
    smtp_port: int | None
    encryption: str | None
    username: str | None
    region: str | None
    from_email: str | None
    from_name: str | None
    access_key_id_configured: bool
    password_configured: bool
    daily_limit: int
    interval_seconds: int
    created_at: str


# ---- 探活结果 ----
class TestResult(BaseModel):
    ok: bool
    error: str | None = None


# ---- 用户验证码 ----
VerificationPurpose = Literal[
    "register",
    "reset_password",
    "login_by_code",
    "change_phone",
    "change_email",
]


class VerificationCodeSendRequest(BaseModel):
    channel: Literal["sms", "email"]
    target: str = Field(min_length=4, max_length=256)
    purpose: VerificationPurpose


class VerificationCodeVerifyRequest(BaseModel):
    channel: Literal["sms", "email"]
    target: str = Field(min_length=4, max_length=256)
    purpose: VerificationPurpose
    code: str = Field(min_length=4, max_length=8)


class VerifyTokenResponse(BaseModel):
    verify_token: str
    expires_in: int  # 秒数


class LoginByCodeRequest(BaseModel):
    verify_token: str


class ResetPasswordRequest(BaseModel):
    verify_token: str
    new_password: str = Field(min_length=6, max_length=128)


class ChangePhoneRequest(BaseModel):
    verify_token: str
    new_phone: str = Field(min_length=4, max_length=32)


class ChangeEmailRequest(BaseModel):
    verify_token: str
    new_email: str = Field(min_length=4, max_length=255)
