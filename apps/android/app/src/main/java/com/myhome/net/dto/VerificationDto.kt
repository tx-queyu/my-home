package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- SmsConfig ----
@Serializable
data class SmsConfigDto(
    val id: String,
    val provider: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("sign_name") val signName: String? = null,
    @SerialName("template_code") val templateCode: String? = null,
    @SerialName("sdk_app_id") val sdkAppId: String? = null,
    val region: String? = null,
    @SerialName("access_key_id_configured") val accessKeyIdConfigured: Boolean = false,
    @SerialName("daily_limit") val dailyLimit: Int = 1000,
    @SerialName("interval_seconds") val intervalSeconds: Int = 60,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SmsConfigCreateRequest(
    val provider: String,
    @SerialName("sign_name") val signName: String? = null,
    @SerialName("template_code") val templateCode: String? = null,
    @SerialName("access_key_id") val accessKeyId: String? = null,
    @SerialName("access_key_secret") val accessKeySecret: String? = null,
    @SerialName("sdk_app_id") val sdkAppId: String? = null,
    val region: String? = null,
    @SerialName("daily_limit") val dailyLimit: Int = 1000,
    @SerialName("interval_seconds") val intervalSeconds: Int = 60,
)

@Serializable
data class SmsConfigUpdateRequest(
    @SerialName("sign_name") val signName: String? = null,
    @SerialName("template_code") val templateCode: String? = null,
    @SerialName("access_key_id") val accessKeyId: String? = null,
    @SerialName("access_key_secret") val accessKeySecret: String? = null,
    @SerialName("sdk_app_id") val sdkAppId: String? = null,
    val region: String? = null,
    @SerialName("daily_limit") val dailyLimit: Int? = null,
    @SerialName("interval_seconds") val intervalSeconds: Int? = null,
)

// ---- EmailConfig ----
@Serializable
data class EmailConfigDto(
    val id: String,
    val provider: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("smtp_host") val smtpHost: String? = null,
    @SerialName("smtp_port") val smtpPort: Int? = null,
    val encryption: String? = null,
    val username: String? = null,
    val region: String? = null,
    @SerialName("from_email") val fromEmail: String? = null,
    @SerialName("from_name") val fromName: String? = null,
    @SerialName("access_key_id_configured") val accessKeyIdConfigured: Boolean = false,
    @SerialName("password_configured") val passwordConfigured: Boolean = false,
    @SerialName("daily_limit") val dailyLimit: Int = 200,
    @SerialName("interval_seconds") val intervalSeconds: Int = 60,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class EmailConfigCreateRequest(
    val provider: String,
    @SerialName("smtp_host") val smtpHost: String? = null,
    @SerialName("smtp_port") val smtpPort: Int? = null,
    val encryption: String? = "ssl",
    val username: String? = null,
    val password: String? = null,
    @SerialName("access_key_id") val accessKeyId: String? = null,
    @SerialName("access_key_secret") val accessKeySecret: String? = null,
    val region: String? = null,
    @SerialName("from_email") val fromEmail: String? = null,
    @SerialName("from_name") val fromName: String? = null,
    @SerialName("daily_limit") val dailyLimit: Int = 200,
    @SerialName("interval_seconds") val intervalSeconds: Int = 60,
)

@Serializable
data class EmailConfigUpdateRequest(
    @SerialName("smtp_host") val smtpHost: String? = null,
    @SerialName("smtp_port") val smtpPort: Int? = null,
    val encryption: String? = null,
    val username: String? = null,
    val password: String? = null,
    @SerialName("access_key_id") val accessKeyId: String? = null,
    @SerialName("access_key_secret") val accessKeySecret: String? = null,
    val region: String? = null,
    @SerialName("from_email") val fromEmail: String? = null,
    @SerialName("from_name") val fromName: String? = null,
    @SerialName("daily_limit") val dailyLimit: Int? = null,
    @SerialName("interval_seconds") val intervalSeconds: Int? = null,
)

@Serializable
data class TestResultDto(
    val ok: Boolean,
    val error: String? = null,
)

// ---- 用户验证码 ----
@Serializable
data class VerificationCodeSendRequest(
    val channel: String, // sms | email
    val target: String,
    val purpose: String, // register | reset_password | login_by_code | change_phone | change_email
)

@Serializable
data class VerificationCodeVerifyRequest(
    val channel: String,
    val target: String,
    val purpose: String,
    val code: String,
)

@Serializable
data class VerifyTokenResponse(
    @SerialName("verify_token") val verifyToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class LoginByCodeRequest(
    @SerialName("verify_token") val verifyToken: String,
)

@Serializable
data class ResetPasswordRequest(
    @SerialName("verify_token") val verifyToken: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class ChangePhoneRequest(
    @SerialName("verify_token") val verifyToken: String,
    @SerialName("new_phone") val newPhone: String,
)

@Serializable
data class ChangeEmailRequest(
    @SerialName("verify_token") val verifyToken: String,
    @SerialName("new_email") val newEmail: String,
)

@Serializable
data class SendCodeResponse(
    val sent: Boolean,
)
