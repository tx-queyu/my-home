package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PointMeDto(
    val balance: Int,
    val recent: List<PointTransactionDto> = emptyList(),
)

@Serializable
data class PointTransactionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val delta: Int,
    val source: String,  // task | redemption | adjustment
    @SerialName("ref_id") val refId: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class FamilyPointAccountDto(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val roles: List<String> = emptyList(),
    val balance: Int = 0,
)
