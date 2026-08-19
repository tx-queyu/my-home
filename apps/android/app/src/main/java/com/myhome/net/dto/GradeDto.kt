package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 学科成绩（v0.17.0，家长录入）。 */
@Serializable
data class GradeDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    val subject: String,
    val score: Double,
    @SerialName("score_full") val scoreFull: Double,
    @SerialName("exam_name") val examName: String? = null,
    @SerialName("exam_date") val examDate: String,
    val note: String? = null,
    @SerialName("assignee_user_id") val assigneeUserId: String,
    @SerialName("assignee_username") val assigneeUsername: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GradeCreateRequest(
    val subject: String,
    val score: Double,
    @SerialName("score_full") val scoreFull: Double = 100.0,
    @SerialName("exam_name") val examName: String? = null,
    @SerialName("exam_date") val examDate: String,
    val note: String? = null,
    @SerialName("assignee_user_id") val assigneeUserId: String,
)

/** 部分更新：null = 不改。 */
@Serializable
data class GradeUpdateRequest(
    val subject: String? = null,
    val score: Double? = null,
    @SerialName("score_full") val scoreFull: Double? = null,
    @SerialName("exam_name") val examName: String? = null,
    @SerialName("exam_date") val examDate: String? = null,
    val note: String? = null,
    @SerialName("assignee_user_id") val assigneeUserId: String? = null,
)
