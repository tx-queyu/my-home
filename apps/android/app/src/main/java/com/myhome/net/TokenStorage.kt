package com.myhome.net

import kotlinx.coroutines.flow.Flow

interface TokenStorage {
    val tokenFlow: Flow<TokenData?>
    suspend fun save(token: TokenData)
    suspend fun get(): TokenData?
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun clear()
}

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
)
