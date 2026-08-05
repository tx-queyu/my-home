package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("course_id") val courseId: String? = null,
    val course: CourseDto? = null,
    val title: String,
    val description: String? = null,
    val points: Int,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("assignee_user_id") val assigneeUserId: String? = null,
    @SerialName("assignee_username") val assigneeUsername: String? = null,
    @SerialName("available_start_date") val availableStartDate: String? = null,
    @SerialName("available_end_date") val availableEndDate: String? = null,
    @SerialName("available_start_time") val availableStartTime: String? = null,
    @SerialName("available_end_time") val availableEndTime: String? = null,
    @SerialName("recurrence_type") val recurrenceType: String = "one_off",
    @SerialName("recurrence_weekdays") val recurrenceWeekdays: List<Int>? = null,
    @SerialName("completed_today") val completedToday: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class TaskRequest(
    val title: String,
    val description: String? = null,
    @SerialName("course_id") val courseId: String? = null,
    val points: Int = 1,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("assignee_user_id") val assigneeUserId: String? = null,
    @SerialName("available_start_date") val availableStartDate: String? = null,
    @SerialName("available_end_date") val availableEndDate: String? = null,
    @SerialName("available_start_time") val availableStartTime: String? = null,
    @SerialName("available_end_time") val availableEndTime: String? = null,
    @SerialName("recurrence_type") val recurrenceType: String = "one_off",
    @SerialName("recurrence_weekdays") val recurrenceWeekdays: List<Int>? = null,
)

@Serializable
data class TaskRecordDto(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("points_earned") val pointsEarned: Int,
    @SerialName("completed_date") val completedDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
