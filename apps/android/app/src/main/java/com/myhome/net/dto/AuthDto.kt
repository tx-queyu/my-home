package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("family_name") val familyName: String,
    @SerialName("verify_token") val verifyToken: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class UserInfo(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val roles: List<String> = emptyList(),
    @SerialName("family_id") val familyId: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    @SerialName("email_verified") val emailVerified: Boolean = false,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: UserInfo,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class ResetPasswordByAdminRequest(
    @SerialName("new_password") val newPassword: String,
)
