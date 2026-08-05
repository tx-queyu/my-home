package com.myhome.repo

import com.myhome.BuildConfig
import com.myhome.net.dto.VersionInfoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun fetchVersionInfo(): VersionInfoDto = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${BuildConfig.BACKEND_URL}version.json")
            .get()
            .build()
        val resp = runCatching { client.newCall(req).execute() }
            .getOrElse { throw IOException("无法连接服务器", it) }
        resp.use {
            if (!it.isSuccessful) throw IOException("服务器返回 ${it.code}")
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("响应为空")
            json.decodeFromString(VersionInfoDto.serializer(), body)
        }
    }

    fun apkDownloadUrl(apkUrl: String): String =
        if (apkUrl.startsWith("http")) apkUrl else "${BuildConfig.BACKEND_URL}${apkUrl.removePrefix("/")}"
}
