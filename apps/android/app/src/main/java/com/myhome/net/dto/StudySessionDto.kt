package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 学习时长上报请求（session 完成时自动埋点）。 */
@Serializable
data class StudySessionReportRequest(
    val subject: String,
    val textbook: String,
    @SerialName("learning_method") val learningMethod: String,
    @SerialName("session_type") val sessionType: String, // reading | learn | quiz
    val source: String, // task | experience | self_study
    @SerialName("duration_seconds") val durationSeconds: Int,
    // null = 服务端补今天
    @SerialName("session_date") val sessionDate: String? = null,
)

/** 学习会话明细。 */
@Serializable
data class StudySessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val subject: String,
    val textbook: String,
    @SerialName("learning_method") val learningMethod: String,
    @SerialName("session_type") val sessionType: String,
    val source: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("session_date") val sessionDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** 按教材聚合的时长分布。 */
@Serializable
data class TextbookTimeDto(
    val subject: String,
    val textbook: String,
    @SerialName("total_seconds") val totalSeconds: Int,
    @SerialName("session_count") val sessionCount: Int,
)

/** 学习时长统计（今日/本周/累计 + 教材分布）。 */
@Serializable
data class StudyStatsDto(
    @SerialName("today_seconds") val todaySeconds: Int,
    @SerialName("week_seconds") val weekSeconds: Int,
    @SerialName("total_seconds") val totalSeconds: Int,
    @SerialName("by_textbook") val byTextbook: List<TextbookTimeDto> = emptyList(),
)
