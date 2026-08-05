package com.myhome.net

import com.myhome.net.dto.RefreshRequest
import com.myhome.storage.AccountStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 401 自动刷新 token + 重试原请求。
 *
 * 触发条件：OkHttp 收到 401 且原请求带 Bearer header。
 *
 * 行为：
 * 1. 用当前活跃 refreshToken 调用 [RefreshApi.refresh]，拿新 accessToken + 可能 rotate 过的新 refreshToken
 * 2. 写回 [TokenStorage]（活跃槽）+ [AccountStore.updateTokens]（持久化多账号条目，保留 savedAt）
 * 3. 用新 accessToken 重试原请求
 *
 * 失败（refresh 调用本身 401/网络错误等）：调 [TokenStorage.clear] 清活跃 token，
 * [com.myhome.ui.nav.RootNavGraph] 现有 tokenFlow.collect → LOGIN 重定向自动发生。
 *
 * 并发：多个请求同时 401 时 [refreshMutex] 串行化 refresh；后到的请求在 mutex 内 re-check
 * `currentAccess != failedToken`，若已被前一线程刷新过则直接用新 token 重试，不重复 refresh。
 *
 * 递归保护：refresh 调用本身 401 时不再触发 Authenticator（encodedPath 命中 `/api/auth/refresh`），
 * 同时 `responseCount >= 2` 提前放弃以保万无一失。
 */
@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val accountStore: AccountStore,
    private val refreshApi: RefreshApi,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // refresh 调用本身的 401 — 不重试，避免递归
        if (response.request.url.encodedPath.endsWith("/api/auth/refresh")) return null
        // 超过 1 次重试放弃
        if (responseCount(response) >= 2) return null
        // 无 Bearer header 的 401（如 login 错误凭证）— 与 token 过期无关，不 refresh
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ") ?: return null

        return runBlocking {
            refreshMutex.withLock {
                // 并发优化：别的线程可能已经在 mutex 内刷新过 token
                val currentAccess = tokenStorage.getAccessToken()
                if (currentAccess != null && currentAccess != failedToken) {
                    return@withLock retryRequest(response.request, currentAccess)
                }

                val refreshToken = tokenStorage.getRefreshToken() ?: return@withLock null
                try {
                    val resp = refreshApi.refresh(RefreshRequest(refreshToken))
                    val newToken = TokenData(resp.accessToken, resp.refreshToken)
                    tokenStorage.save(newToken)
                    accountStore.updateTokens(resp.user.id, newToken)
                    retryRequest(response.request, newToken.accessToken)
                } catch (e: Exception) {
                    // refresh 失败 → 清活跃 token，RootNavGraph 自动跳 LOGIN
                    tokenStorage.clear()
                    null
                }
            }
        }
    }

    private fun retryRequest(original: Request, accessToken: String): Request =
        original.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
