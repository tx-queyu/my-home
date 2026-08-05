"""角色枚举组配置。

同组角色互斥 — 一个用户最多只能同时拥有同组中的 1 个角色。
角色若不在此 dict 中，则无组限制，可与其他任何角色叠加（如 family_admin、admin）。

新增角色时，若希望其与某组互斥，把角色名映射到组名即可；若要新建组，直接引入新组名。
"""
ROLE_EXCLUSIVE_GROUPS: dict[str, str] = {
    "parent": "family_identity",
    "child": "family_identity",
}


def validate_role_groups(roles: list[str]) -> None:
    """校验角色组合是否符合组互斥约束。

    Raises:
        ValueError("role_group_conflict")：同组角色超过 1 个
    """
    groups_seen: set[str] = set()
    for r in roles:
        group = ROLE_EXCLUSIVE_GROUPS.get(r)
        if not group:
            continue
        if group in groups_seen:
            raise ValueError("role_group_conflict")
        groups_seen.add(group)
