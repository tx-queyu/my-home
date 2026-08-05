package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 我的自学教材（v0.16.1）：教材 + 该教材下 active 课程。 */
@Serializable
data class SelfStudyTextbookDto(
    val id: String,
    val subject: String,
    val textbook: String,
    val courses: List<CourseDto> = emptyList(),
)

/** 可添加的教材选项（系统 active 课程聚合）。 */
@Serializable
data class TextbookOptionDto(
    val subject: String,
    val textbook: String,
    val courses: List<CourseDto> = emptyList(),
)

@Serializable
data class SelfStudyTextbookCreateRequest(
    val subject: String,
    val textbook: String,
)
