package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemUserDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val roles: List<String> = emptyList(),
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class SystemUserPageDto(
    val items: List<SystemUserDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

@Serializable
data class SystemUserUpdateRequest(
    val roles: List<String>,
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class SystemUserCreateRequest(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    val roles: List<String>,
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class SystemFamilyDto(
    val id: String,
    val name: String,
    @SerialName("member_count") val memberCount: Int = 0,
)

@Serializable
data class SystemFamilyPageDto(
    val items: List<SystemFamilyDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

@Serializable
data class SystemFamilyDetailDto(
    val id: String,
    val name: String,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    val members: List<SystemUserDto> = emptyList(),
)

@Serializable
data class SystemRoleDto(
    val role: String,
    val count: Int = 0,
    val description: String,
    @SerialName("exclusive_group") val exclusiveGroup: String? = null,
)

