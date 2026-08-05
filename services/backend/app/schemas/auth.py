"""认证相关 Pydantic 模型。"""
from pydantic import BaseModel, Field


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=64)
    family_name: str = Field(min_length=1, max_length=128)
    # 可选：注册时绑定已验证手机号/邮箱（需先调 /api/auth/verification-code/verify 拿 verify_token）
    verify_token: str | None = None
    phone: str | None = Field(default=None, max_length=32)
    email: str | None = Field(default=None, max_length=255)


class LoginRequest(BaseModel):
    username: str
    password: str


class TokenRefreshRequest(BaseModel):
    refresh_token: str


class UserInfo(BaseModel):
    id: str
    username: str
    display_name: str
    roles: list[str]
    family_id: str | None = None
    phone: str | None = None
    email: str | None = None
    phone_verified: bool = False
    email_verified: bool = False

    model_config = {"from_attributes": True}


class AuthResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: UserInfo


class ChangePasswordRequest(BaseModel):
    current_password: str = Field(min_length=6, max_length=128)
    new_password: str = Field(min_length=6, max_length=128)


class ResetPasswordByAdminRequest(BaseModel):
    new_password: str = Field(min_length=6, max_length=128)
