package com.myhome.util

object RoleUtil {
    fun label(role: String): String = when (role) {
        "family_admin" -> "家庭管理员"
        "parent" -> "家长"
        "child" -> "孩子"
        "admin" -> "系统管理员"
        else -> role
    }

    fun label(roles: Collection<String>): String =
        roles.sortedBy { order(it) }.joinToString(" · ") { label(it) }.ifEmpty { "无角色" }

    private fun order(role: String): Int = when (role) {
        "family_admin" -> 0
        "parent" -> 1
        "child" -> 2
        "admin" -> 3
        else -> 99
    }

    /** 是否拥有家庭管理权限（管理家庭成员增删、布置任务等）。family_admin 与 parent 都算。 */
    fun canManageFamily(roles: Collection<String>): Boolean =
        roles.any { it == "family_admin" || it == "parent" }

    /** 是否家庭管理员（可授权/收回其他成员的家庭管理员身份）。 */
    fun isFamilyAdmin(roles: Collection<String>): Boolean = "family_admin" in roles

    fun hasRole(roles: Collection<String>, role: String): Boolean = role in roles
}
