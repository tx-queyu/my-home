package com.myhome.net

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object JwtUtil {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /**
     * 从 access_token 中解析角色列表。
     * - 新版 token 含 "roles": ["role1","role2"] 数组
     * - 旧版 token 含 "role": "single_role" 字符串
     */
    fun extractRoles(accessToken: String?): List<String> {
        if (accessToken == null) return emptyList()
        val parts = accessToken.split(".")
        if (parts.size != 3) return emptyList()
        return runCatching {
            val payload = Base64.getUrlDecoder().decode(parts[1]).decodeToString()
            val obj = json.parseToJsonElement(payload).jsonObject
            obj["roles"]?.jsonArray?.map { it.jsonPrimitive.content }?.takeIf { it.isNotEmpty() }
                ?: obj["role"]?.jsonPrimitive?.content?.let { listOf(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
