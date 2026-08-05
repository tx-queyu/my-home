package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.TokenData
import com.myhome.net.TokenStorage
import com.myhome.net.dto.LoginRequest
import com.myhome.net.dto.RegisterRequest
import com.myhome.net.dto.ChangePasswordRequest
import com.myhome.net.dto.UserInfo
import com.myhome.storage.AccountStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStorage: TokenStorage,
    private val accountStore: AccountStore,
) {
    val tokenFlow = tokenStorage.tokenFlow
    val accountsFlow = accountStore.accountsFlow

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        familyName: String,
        verifyToken: String? = null,
        phone: String? = null,
        email: String? = null,
    ): UserInfo {
        val resp = api.register(
            RegisterRequest(
                username = username,
                password = password,
                displayName = displayName,
                familyName = familyName,
                verifyToken = verifyToken,
                phone = phone,
                email = email,
            )
        )
        val token = TokenData(resp.accessToken, resp.refreshToken)
        tokenStorage.save(token)
        accountStore.saveAccount(resp.user, token)
        return resp.user
    }

    suspend fun login(username: String, password: String): UserInfo {
        val resp = api.login(LoginRequest(username, password))
        val token = TokenData(resp.accessToken, resp.refreshToken)
        tokenStorage.save(token)
        accountStore.saveAccount(resp.user, token)
        return resp.user
    }

    suspend fun me(): UserInfo = api.getMe()

    suspend fun changePassword(currentPassword: String, newPassword: String) =
        api.changePassword(ChangePasswordRequest(currentPassword, newPassword))

    /**
     * 拉当前用户信息，并确保已在 AccountStore 留底。
     * 用于 v0.5.1→v0.5.2+ 升级用户首次打开切换账号页：原 token 还在，但 AccountStore 是空的，
     * 不补存的话切走就切不回来了。
     */
    suspend fun ensureCurrentAccountSaved(): UserInfo {
        val me = api.getMe()
        if (accountStore.getAccount(me.id) == null) {
            val token = tokenStorage.get()
            if (token != null) accountStore.saveAccount(me, token)
        }
        return me
    }

    /**
     * 切换到已保存的账号 —— 直接复用本地保存的 token，无网络往返。
     * 若 token 已过期，下次 API 调用会 401，调用方按未登录处理即可。
     */
    suspend fun switchToAccount(userId: String): UserInfo? {
        val acc = accountStore.getAccount(userId) ?: return null
        tokenStorage.save(TokenData(acc.accessToken, acc.refreshToken))
        return UserInfo(
            id = acc.id,
            username = acc.username,
            displayName = acc.displayName,
            roles = acc.effectiveRoles,
            familyId = null,
        )
    }

    fun forgetAccount(userId: String) {
        accountStore.removeAccount(userId)
    }

    suspend fun logout() = tokenStorage.clear()
}
