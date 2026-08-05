package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.CreateMemberRequest
import com.myhome.net.dto.FamilyInfo
import com.myhome.net.dto.MemberInfo
import com.myhome.net.dto.ResetPasswordByAdminRequest
import com.myhome.net.dto.UpdateMemberRolesRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun getMyFamily(): FamilyInfo = api.getMyFamily()

    suspend fun listMembers(): List<MemberInfo> = api.listMembers()

    suspend fun createMember(
        username: String,
        password: String,
        displayName: String,
        role: String = "child",
    ): MemberInfo = api.createMember(CreateMemberRequest(username, password, displayName, role))

    suspend fun deleteMember(id: String) = api.deleteMember(id)

    suspend fun resetMemberPassword(memberId: String, newPassword: String) =
        api.resetMemberPassword(memberId, ResetPasswordByAdminRequest(newPassword))

    suspend fun updateMemberRoles(id: String, roles: List<String>): MemberInfo =
        api.updateMemberRoles(id, UpdateMemberRolesRequest(roles))
}
