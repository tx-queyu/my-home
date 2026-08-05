package com.myhome.net

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.header("Authorization") != null) return chain.proceed(req)
        val token = kotlinx.coroutines.runBlocking { tokenStorage.getAccessToken() }
        val authedReq = if (token != null) {
            req.newBuilder().header("Authorization", "Bearer $token").build()
        } else req
        return chain.proceed(authedReq)
    }
}
