"""系统管理员视角的 Pydantic 模型。"""
import re
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator

from app.core.role_groups import ROLE_EXCLUSIVE_GROUPS, validate_role_groups


class SystemUserOut(BaseModel):
    """管理员视角的用户视图，含 family_id / family_name / is_active。"""

    id: str
    username: str
    display_name: str
    roles: list[str]
    family_id: str | None = None
    family_name: str | None = None
    is_active: bool

    model_config = {"from_attributes": True}


class SystemUserUpdateRequest(BaseModel):
    """管理员修改用户：可改 roles、family_id、is_active。

    允许 roles 为空（用户既无角色也无家庭），但此时 family_id 必须为 None。
    """

    roles: list[str] = Field(default_factory=list)
    family_id: str | None = None
    is_active: bool = True

    @field_validator("roles")
    @classmethod
    def _validate_roles(cls, v: list[str]) -> list[str]:
        allowed = {"parent", "child", "family_admin", "admin"}
        for r in v:
            if r not in allowed:
                raise ValueError(f"invalid role: {r}")
        validate_role_groups(v)
        return v

    @model_validator(mode="after")
    def _validate_family(self):
        has_family_role = any(r != "admin" for r in self.roles)
        if has_family_role and not self.family_id:
            raise ValueError("family_id_required")
        if not has_family_role and self.roles and self.family_id:
            # roles 全是 admin 却指定 family_id — 不允许
            raise ValueError("family_id_not_allowed")
        return self


class SystemUserCreateRequest(BaseModel):
    """管理员创建用户：指定 username/password/display_name/roles/family_id。"""

    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=6, max_length=64)
    display_name: str = Field(min_length=1, max_length=32)
    roles: list[str] = Field(min_length=1)
    family_id: str | None = None
    is_active: bool = True

    @field_validator("username")
    @classmethod
    def _validate_username(cls, v: str) -> str:
        if not re.fullmatch(r"[a-zA-Z0-9_]+", v):
            raise ValueError("username 只能含字母、数字、下划线")
        return v

    @field_validator("roles")
    @classmethod
    def _validate_roles(cls, v: list[str]) -> list[str]:
        allowed = {"parent", "child", "family_admin", "admin"}
        for r in v:
            if r not in allowed:
                raise ValueError(f"invalid role: {r}")
        validate_role_groups(v)
        return v

    @model_validator(mode="after")
    def _validate_family(self):
        has_family_role = any(r != "admin" for r in self.roles)
        if has_family_role and not self.family_id:
            raise ValueError("family_id_required")
        if not has_family_role and self.family_id:
            raise ValueError("family_id_not_allowed")
        return self


class SystemFamilyOut(BaseModel):
    """管理员视角的家庭视图，含 member_count。"""

    id: str
    name: str
    member_count: int = 0

    model_config = {"from_attributes": True}


class SystemFamilyDetailOut(BaseModel):
    """家庭详情：基本信息 + 成员列表。"""

    id: str
    name: str
    member_count: int = 0
    created_at: datetime
    members: list[SystemUserOut] = []

    model_config = {"from_attributes": True}


class SystemRoleOut(BaseModel):
    """角色统计：role + count + 描述 + 所属角色枚举组。"""

    role: str
    count: int = 0
    description: str
    exclusive_group: str | None = None

    model_config = {"from_attributes": True}


class SystemUserPage(BaseModel):
    """用户分页响应：items + total + 当前页 page/size。"""

    items: list[SystemUserOut]
    total: int
    page: int
    size: int


class SystemFamilyPage(BaseModel):
    """家庭分页响应：items + total + 当前页 page/size。"""

    items: list[SystemFamilyOut]
    total: int
    page: int
    size: int
