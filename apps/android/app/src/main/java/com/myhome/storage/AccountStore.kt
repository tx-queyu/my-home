package com.myhome.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.myhome.net.TokenData
import com.myhome.net.dto.UserInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多账号持久化 —— 登录成功后 saveAccount，切换账号时 listAccounts 给 UI 渲染，tap 后 restore 出 token。
 *
 * 复用 EncryptedSharedPreferences（与 EncryptedTokenStorage 同一加密底座）：
 * 家庭 App 有 child 账号，设备 root / 备份提取时不希望多账号 token 裸露在明文 DataStore 里。
 */
@Singleton
class AccountStore @Inject constructor(
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    @Serializable
    data class SavedAccount(
        val id: String,
        val username: String,
        val displayName: String,
        val roles: List<String> = emptyList(),
        @Suppress("unused") @SerialName("role") private val legacyRole: String? = null,
        val accessToken: String,
        val refreshToken: String,
        val savedAt: Long,
    ) {
        /** 旧持久化数据可能存的是单 role，迁移成 [role]；新版直接返回 roles。 */
        val effectiveRoles: List<String> get() = roles.ifEmpty { listOfNotNull(legacyRole) }
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "myhome_accounts",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _accountsFlow = MutableStateFlow(loadFromDisk())
    val accountsFlow: StateFlow<List<SavedAccount>> = _accountsFlow.asStateFlow()

    fun list(): List<SavedAccount> = _accountsFlow.value

    fun saveAccount(user: UserInfo, token: TokenData) {
        val current = list()
        val filtered = current.filter { it.id != user.id }
        val updated = filtered + SavedAccount(
            id = user.id,
            username = user.username,
            displayName = user.displayName,
            roles = user.roles,
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            savedAt = System.currentTimeMillis(),
        )
        writeAll(updated.sortedByDescending { it.savedAt })
    }

    fun removeAccount(userId: String) {
        writeAll(list().filter { it.id != userId })
    }

    fun getAccount(userId: String): SavedAccount? = list().firstOrNull { it.id == userId }

    /**
     * 写回 refresh 后的新 token（accessToken + 可能 rotate 过的 refreshToken），保留原 savedAt 不重排账号列表。
     * 由 [com.myhome.net.AuthAuthenticator] 在 401 自动 refresh 成功后调用。
     */
    fun updateTokens(userId: String, token: TokenData) {
        val current = list()
        val updated = current.map { acc ->
            if (acc.id == userId) acc.copy(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
            ) else acc
        }
        writeAll(updated)
    }

    private fun loadFromDisk(): List<SavedAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SavedAccount.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun writeAll(accounts: List<SavedAccount>) {
        prefs.edit().apply {
            if (accounts.isEmpty()) remove(KEY_ACCOUNTS)
            else putString(KEY_ACCOUNTS, json.encodeToString(ListSerializer(SavedAccount.serializer()), accounts))
        }.apply()
        _accountsFlow.value = accounts
    }

    private companion object {
        const val KEY_ACCOUNTS = "saved_accounts_json"
    }
}
