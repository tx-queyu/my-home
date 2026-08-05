package com.myhome.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 把后端 ISO 8601 UTC 时间字符串（如 "2026-08-04T12:34:56.789+00:00"）
 * 转成本地时区的可读时间。
 *
 * 后端 last_seen / created_at 等字段是 `DateTime(timezone=True)`，Pydantic 序列化时带 UTC 偏移。
 * 直接 toString 会显示 UTC 时间误导用户（比北京时间晚 8 小时）。
 *
 * 解析失败时返回原字符串，避免崩溃。
 *
 * @param shortFormat true=列表行用的紧凑格式 "MM-dd HH:mm"，false=详情页用的完整格式 "yyyy-MM-dd HH:mm"
 */
fun String?.toLocalSeenText(shortFormat: Boolean = false): String {
    if (this.isNullOrBlank()) return "—"
    return runCatching {
        val parsed = OffsetDateTime.parse(this)
        val zoned = parsed.atZoneSameInstant(ZoneId.systemDefault())
        val pattern = if (shortFormat) "MM-dd HH:mm" else "yyyy-MM-dd HH:mm"
        zoned.format(DateTimeFormatter.ofPattern(pattern))
    }.getOrElse { this }
}
