package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FamilyInfo(
    val id: String,
    val name: String,
)

@Serializable
data class MemberInfo(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val roles: List<String> = emptyList(),
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class CreateMemberRequest(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    val role: String = "child",
)

@Serializable
data class UpdateMemberRolesRequest(
    val roles: List<String>,
)
