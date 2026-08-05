package com.myhome.net

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedSharedPreferences-backed token storage.
 *
 * 用 EncryptedSharedPreferences 而非明文 DataStore：家庭 App 有 child 账号，
 * 设备 root / 备份提取情况下不希望 access_token 裸露。
 */
@Singleton
class EncryptedTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStorage {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "myhome_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _tokenFlow = MutableStateFlow(loadFromDisk())
    override val tokenFlow: StateFlow<TokenData?> = _tokenFlow.asStateFlow()

    override suspend fun save(token: TokenData) {
        prefs.edit()
            .putString(KEY_ACCESS, token.accessToken)
            .putString(KEY_REFRESH, token.refreshToken)
            .apply()
        _tokenFlow.value = token
    }

    override suspend fun get(): TokenData? = loadFromDisk()

    override suspend fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)

    override suspend fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    override suspend fun clear() {
        prefs.edit().clear().apply()
        _tokenFlow.value = null
    }

    private fun loadFromDisk(): TokenData? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return TokenData(access, refresh)
    }

    private companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
    }
}
