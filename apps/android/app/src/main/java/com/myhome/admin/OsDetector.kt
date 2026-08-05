package com.myhome.admin

import android.os.Build
import android.util.Log

/**
 * 检测当前设备是否运行 HarmonyOS（含 HarmonyOS NEXT）。
 *
 * HarmonyOS 4.x（基于 AOSP）：Build.VERSION.RELEASE 含 "HarmonyOS" 或 Build.DISPLAY 含 "Harmony" / 鸿蒙
 * HarmonyOS NEXT 5.0+（纯鸿蒙，不基于 Android）：尝试加载 ohos 命名空间下的类（如 ohos.app.Application）
 *
 * 即使 myhome 以 APK 形式跑在鸿蒙兼容层上，Build.MANUFACTURER 仍是 "HUAWEI"，
 * 我们一律判定为 harmony，让 UI 走鸿蒙专属提示路径。
 */
object OsDetector {
    private const val TAG = "OsDetector"

    fun osType(): String = if (isHarmonyOs()) "harmony" else "android"

    fun isHarmonyOs(): Boolean {
        if (Build.MANUFACTURER?.equals("HUAWEI", ignoreCase = true) != true &&
            Build.BRAND?.equals("HUAWEI", ignoreCase = true) != true &&
            Build.MANUFACTURER?.equals("HONOR", ignoreCase = true) != true &&
            Build.BRAND?.equals("HONOR", ignoreCase = true) != true
        ) {
            return false
        }
        // 华为设备：再检查系统属性是否声明鸿蒙
        val release = Build.VERSION.RELEASE.orEmpty()
        val display = Build.DISPLAY.orEmpty()
        if (release.contains("HarmonyOS", ignoreCase = true) ||
            release.contains("鸿蒙", ignoreCase = true) ||
            display.contains("Harmony", ignoreCase = true) ||
            display.contains("鸿蒙", ignoreCase = true)
        ) {
            return true
        }
        // 兜底：尝试反射加载 ohos 类（HarmonyOS NEXT 5.0+ 有原生 ohos 命名空间）
        return try {
            Class.forName("ohos.app.Application")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    fun osVersion(): String {
        val release = Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" }
        val sdk = Build.VERSION.SDK_INT
        return if (isHarmonyOs()) {
            // 鸿蒙版本号优先从 Build.DISPLAY 提取；取不到就标 release
            val harmonyVer = extractHarmonyVersion() ?: release
            "HarmonyOS $harmonyVer (API $sdk)"
        } else {
            "Android $release (API $sdk)"
        }
    }

    fun manufacturer(): String = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" }

    fun model(): String = Build.MODEL.orEmpty().ifBlank { "unknown" }

    private fun extractHarmonyVersion(): String? {
        val candidates = listOf(Build.DISPLAY, Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL)
        for (raw in candidates) {
            val s = raw ?: continue
            val idx = s.indexOf("HarmonyOS", ignoreCase = true)
            if (idx < 0) continue
            val tail = s.substring(idx + "HarmonyOS".length).trimStart()
            val end = tail.indexOfAny(charArrayOf(' ', '-', '_', ')', '(', ';', ','))
            val ver = if (end < 0) tail else tail.substring(0, end)
            if (ver.isNotEmpty()) return ver
        }
        return null
    }
}
