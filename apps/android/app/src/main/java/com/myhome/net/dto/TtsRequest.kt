package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TtsRequest(
    val text: String,
    val voice: String? = null,
    val rate: String = "+0%",
    val volume: String = "+0%",
)

@Serializable
data class TtsVoiceDto(
    val id: String,
    val lang: String,
    val gender: String,
    val label: String,
)

@Serializable
data class TtsVoicesResponse(
    val voices: List<TtsVoiceDto>,
)
