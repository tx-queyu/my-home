package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApplianceDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val type: String,
    val location: String,
    val status: String,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ApplianceRequest(
    val name: String,
    val type: String,
    val location: String,
    val status: String = "normal",
    val notes: String? = null,
)
