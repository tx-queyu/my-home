package com.myhome.net

import com.myhome.net.dto.AuthResponse
import com.myhome.net.dto.RefreshRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 独立的 refresh Retrofit 接口，挂在无 [AuthInterceptor] / [AuthAuthenticator] 的 OkHttpClient 上，
 * 避免 [AuthAuthenticator] 调用 refresh 时 401 触发自身递归。
 */
interface RefreshApi {
    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse
}
