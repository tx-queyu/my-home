"""家庭相关 Pydantic 模型。"""
from pydantic import BaseModel, Field, field_validator


class FamilyInfo(BaseModel):
    id: str
    name: str

    model_config = {"from_attributes": True}


class MemberInfo(BaseModel):
    id: str
    username: str
    display_name: str
    roles: list[str]
    is_active: bool

    model_config = {"from_attributes": True}


class CreateMemberRequest(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=64)
    role: str = Field(default="child", pattern="^(parent|child)$")


class UpdateMemberRolesRequest(BaseModel):
    """家庭管理员授权/收回其他成员的角色。允许 parent/child/family_admin，至少 1 项。"""

    roles: list[str] = Field(min_length=1)

    @field_validator("roles")
    @classmethod
    def _validate_roles(cls, v: list[str]) -> list[str]:
        allowed = {"parent", "child", "family_admin"}
        for r in v:
            if r not in allowed:
                raise ValueError(f"invalid role: {r}")
        return v
