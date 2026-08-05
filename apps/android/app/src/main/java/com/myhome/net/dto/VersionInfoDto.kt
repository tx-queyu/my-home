package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionInfoDto(
    val version: String,
    @SerialName("apk_url") val apkUrl: String,
    val description: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
)
