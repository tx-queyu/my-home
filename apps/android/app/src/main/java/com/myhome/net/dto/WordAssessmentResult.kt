package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WordAssessmentResult(
    @SerialName("word_id") val wordId: String,
    @SerialName("ref_text") val refText: String,
    val score: Int = 0,
    val passed: Boolean = false,
    val enabled: Boolean = true,  // false 表示 ISE 未配置，前端降级处理
)
