package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RewardDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val description: String? = null,
    val cost: Int,
    val stock: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RewardRequest(
    val name: String,
    val description: String? = null,
    val cost: Int,
    val stock: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class RedemptionDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("reward_id") val rewardId: String,
    @SerialName("reward_name") val rewardName: String? = null,
    val cost: Int,
    val status: String,  // pending | fulfilled | rejected
    @SerialName("handled_at") val handledAt: String? = null,
    @SerialName("handled_by") val handledBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RedeemRequest(
    @SerialName("reward_id") val rewardId: String,
)
