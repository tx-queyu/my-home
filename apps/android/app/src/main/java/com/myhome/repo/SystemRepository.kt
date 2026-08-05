package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.SystemFamilyDetailDto
import com.myhome.net.dto.SystemFamilyDto
import com.myhome.net.dto.SystemFamilyPageDto
import com.myhome.net.dto.SystemRoleDto
import com.myhome.net.dto.SystemUserCreateRequest
import com.myhome.net.dto.SystemUserDto
import com.myhome.net.dto.SystemUserPageDto
import com.myhome.net.dto.SystemUserUpdateRequest
import com.myhome.net.dto.ResetPasswordByAdminRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun listUsersPage(
        page: Int = 1,
        size: Int = 20,
        familyId: String? = null,
        role: String? = null,
        active: Boolean? = null,
        q: String? = null,
    ): SystemUserPageDto = api.listSystemUsers(
        page = page,
        size = size,
        familyId = familyId,
        role = role,
        active = active,
        q = q,
    )

    /** 不分页版（用于拉取候选成员列表等需要全量的场景）。后端 size 上限 100，循环拉取所有页。 */
    suspend fun listAllUsers(): List<SystemUserDto> {
        val all = mutableListOf<SystemUserDto>()
        var page = 1
        while (true) {
            val pg = api.listSystemUsers(page = page, size = 100)
            all += pg.items
            if (all.size >= pg.total || pg.items.isEmpty()) break
            page += 1
        }
        return all
    }

    suspend fun listFamilies(): List<SystemFamilyDto> {
        val all = mutableListOf<SystemFamilyDto>()
        var page = 1
        while (true) {
            val pg = api.listSystemFamilies(page = page, size = 100)
            all += pg.items
            if (all.size >= pg.total || pg.items.isEmpty()) break
            page += 1
        }
        return all
    }

    suspend fun listFamiliesPage(
        page: Int = 1,
        size: Int = 20,
        q: String? = null,
        hasMembers: Boolean? = null,
    ): SystemFamilyPageDto = api.listSystemFamilies(
        page = page,
        size = size,
        q = q,
        hasMembers = hasMembers,
    )

    suspend fun getFamily(id: String): SystemFamilyDetailDto = api.getSystemFamilyDetail(id)

    suspend fun deleteFamily(id: String) = api.deleteSystemFamily(id)

    suspend fun listRoles(): List<SystemRoleDto> = api.listSystemRoles()

    suspend fun updateUser(
        userId: String,
        roles: List<String>,
        familyId: String?,
        isActive: Boolean,
    ): SystemUserDto = api.updateSystemUser(
        userId,
        SystemUserUpdateRequest(roles = roles, familyId = familyId, isActive = isActive),
    )

    suspend fun createUser(
        username: String,
        password: String,
        displayName: String,
        roles: List<String>,
        familyId: String?,
        isActive: Boolean,
    ): SystemUserDto = api.createSystemUser(
        SystemUserCreateRequest(
            username = username,
            password = password,
            displayName = displayName,
            roles = roles,
            familyId = familyId,
            isActive = isActive,
        ),
    )

    suspend fun deleteUser(userId: String) = api.deleteSystemUser(userId)

    suspend fun resetUserPassword(userId: String, newPassword: String) =
        api.adminResetUserPassword(userId, ResetPasswordByAdminRequest(newPassword))
}
