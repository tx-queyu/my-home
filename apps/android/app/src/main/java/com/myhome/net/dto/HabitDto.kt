package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 习惯定义（v0.17.0 每日打卡）。 */
@Serializable
data class HabitDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val points: Int,
    @SerialName("streak_cap") val streakCap: Int,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("today_checked_in") val todayCheckedIn: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class HabitCreateRequest(
    val name: String,
    val points: Int = 1,
    @SerialName("streak_cap") val streakCap: Int = 7,
    @SerialName("is_active") val isActive: Boolean = true,
)

/** 部分更新：null = 不改。 */
@Serializable
data class HabitUpdateRequest(
    val name: String? = null,
    val points: Int? = null,
    @SerialName("streak_cap") val streakCap: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

/** 打卡日志。 */
@Serializable
data class HabitLogDto(
    val id: String,
    @SerialName("habit_id") val habitId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("habit_name") val habitName: String? = null,
    val username: String? = null,
    @SerialName("streak_count") val streakCount: Int,
    @SerialName("points_earned") val pointsEarned: Int,
    @SerialName("checkin_date") val checkinDate: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
