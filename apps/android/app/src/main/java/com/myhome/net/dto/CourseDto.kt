package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CourseDto(
    val id: String,
    val subject: String,
    val textbook: String,
    @SerialName("learning_method") val learningMethod: String,
    val description: String? = null,
    @SerialName("default_points") val defaultPoints: Int = 10,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

fun CourseDto.displayLabel(): String = "$textbook · $learningMethod"

/** 英语课程的互动 session 形态（v0.15.0：朗读/学习/测评）。 */
enum class CourseSessionType { READING, LEARN, QUIZ }

fun CourseDto.sessionType(): CourseSessionType? {
    if (subject != "英语") return null
    return when (learningMethod) {
        "朗读" -> CourseSessionType.READING
        "学习" -> CourseSessionType.LEARN
        "测评" -> CourseSessionType.QUIZ
        else -> null
    }
}

@Serializable
data class CourseExperienceRequest(
    @SerialName("child_id") val childId: String? = null,
)

@Serializable
data class CourseExperienceResult(
    @SerialName("task_title") val taskTitle: String,
    @SerialName("points_earned") val pointsEarned: Int,
    @SerialName("child_username") val childUsername: String,
)
