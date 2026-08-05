package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.TokenData
import com.myhome.net.TokenStorage
import com.myhome.net.dto.AuthResponse
import com.myhome.net.dto.ChangeEmailRequest
import com.myhome.net.dto.ChangePhoneRequest
import com.myhome.net.dto.EmailConfigCreateRequest
import com.myhome.net.dto.EmailConfigDto
import com.myhome.net.dto.EmailConfigUpdateRequest
import com.myhome.net.dto.LoginByCodeRequest
import com.myhome.net.dto.ResetPasswordRequest
import com.myhome.net.dto.SmsConfigCreateRequest
import com.myhome.net.dto.SmsConfigDto
import com.myhome.net.dto.SmsConfigUpdateRequest
import com.myhome.net.dto.TestResultDto
import com.myhome.net.dto.UserInfo
import com.myhome.net.dto.VerificationCodeSendRequest
import com.myhome.net.dto.VerificationCodeVerifyRequest
import com.myhome.net.dto.VerifyTokenResponse
import com.myhome.storage.AccountStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerificationRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStorage: TokenStorage,
    private val accountStore: AccountStore,
) {
    // ---- 用户验证码 ----
    suspend fun sendCode(channel: String, target: String, purpose: String) {
        api.sendVerificationCode(VerificationCodeSendRequest(channel, target, purpose))
    }

    suspend fun verifyCode(
        channel: String,
        target: String,
        purpose: String,
        code: String,
    ): VerifyTokenResponse = api.verifyCode(
        VerificationCodeVerifyRequest(channel, target, purpose, code)
    )

    suspend fun loginByCode(verifyToken: String): UserInfo {
        val resp: AuthResponse = api.loginByCode(LoginByCodeRequest(verifyToken))
        val token = TokenData(resp.accessToken, resp.refreshToken)
        tokenStorage.save(token)
        accountStore.saveAccount(resp.user, token)
        return resp.user
    }

    suspend fun resetPassword(verifyToken: String, newPassword: String) {
        api.resetPassword(ResetPasswordRequest(verifyToken, newPassword))
    }

    suspend fun changePhone(verifyToken: String, newPhone: String): UserInfo {
        val user = api.changePhone(ChangePhoneRequest(verifyToken, newPhone))
        val token = tokenStorage.get()
        if (token != null) accountStore.saveAccount(user, token)
        return user
    }

    suspend fun changeEmail(verifyToken: String, newEmail: String): UserInfo {
        val user = api.changeEmail(ChangeEmailRequest(verifyToken, newEmail))
        val token = tokenStorage.get()
        if (token != null) accountStore.saveAccount(user, token)
        return user
    }

    // ---- SmsConfig CRUD ----
    suspend fun listSmsConfigs(): List<SmsConfigDto> = api.listSmsConfigs()

    suspend fun createSmsConfig(
        provider: String,
        signName: String?,
        templateCode: String?,
        accessKeyId: String?,
        accessKeySecret: String?,
        sdkAppId: String?,
        region: String?,
        dailyLimit: Int,
        intervalSeconds: Int,
    ): SmsConfigDto = api.createSmsConfig(
        SmsConfigCreateRequest(
            provider = provider,
            signName = signName,
            templateCode = templateCode,
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
            sdkAppId = sdkAppId,
            region = region,
            dailyLimit = dailyLimit,
            intervalSeconds = intervalSeconds,
        )
    )

    suspend fun updateSmsConfig(
        id: String,
        signName: String? = null,
        templateCode: String? = null,
        accessKeyId: String? = null,
        accessKeySecret: String? = null,
        sdkAppId: String? = null,
        region: String? = null,
        dailyLimit: Int? = null,
        intervalSeconds: Int? = null,
    ): SmsConfigDto = api.updateSmsConfig(
        id,
        SmsConfigUpdateRequest(
            signName = signName,
            templateCode = templateCode,
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
            sdkAppId = sdkAppId,
            region = region,
            dailyLimit = dailyLimit,
            intervalSeconds = intervalSeconds,
        )
    )

    suspend fun deleteSmsConfig(id: String) = api.deleteSmsConfig(id)
    suspend fun activateSmsConfig(id: String): SmsConfigDto = api.activateSmsConfig(id)
    suspend fun deactivateSmsConfig(id: String): SmsConfigDto = api.deactivateSmsConfig(id)
    suspend fun testSmsConfig(id: String): TestResultDto = api.testSmsConfig(id)

    // ---- EmailConfig CRUD ----
    suspend fun listEmailConfigs(): List<EmailConfigDto> = api.listEmailConfigs()

    suspend fun createEmailConfig(
        provider: String,
        smtpHost: String?,
        smtpPort: Int?,
        encryption: String?,
        username: String?,
        password: String?,
        accessKeyId: String?,
        accessKeySecret: String?,
        region: String?,
        fromEmail: String?,
        fromName: String?,
        dailyLimit: Int,
        intervalSeconds: Int,
    ): EmailConfigDto = api.createEmailConfig(
        EmailConfigCreateRequest(
            provider = provider,
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            encryption = encryption,
            username = username,
            password = password,
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
            region = region,
            fromEmail = fromEmail,
            fromName = fromName,
            dailyLimit = dailyLimit,
            intervalSeconds = intervalSeconds,
        )
    )

    suspend fun updateEmailConfig(
        id: String,
        smtpHost: String? = null,
        smtpPort: Int? = null,
        encryption: String? = null,
        username: String? = null,
        password: String? = null,
        accessKeyId: String? = null,
        accessKeySecret: String? = null,
        region: String? = null,
        fromEmail: String? = null,
        fromName: String? = null,
        dailyLimit: Int? = null,
        intervalSeconds: Int? = null,
    ): EmailConfigDto = api.updateEmailConfig(
        id,
        EmailConfigUpdateRequest(
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            encryption = encryption,
            username = username,
            password = password,
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
            region = region,
            fromEmail = fromEmail,
            fromName = fromName,
            dailyLimit = dailyLimit,
            intervalSeconds = intervalSeconds,
        )
    )

    suspend fun deleteEmailConfig(id: String) = api.deleteEmailConfig(id)
    suspend fun activateEmailConfig(id: String): EmailConfigDto = api.activateEmailConfig(id)
    suspend fun deactivateEmailConfig(id: String): EmailConfigDto = api.deactivateEmailConfig(id)
    suspend fun testEmailConfig(id: String): TestResultDto = api.testEmailConfig(id)
}
