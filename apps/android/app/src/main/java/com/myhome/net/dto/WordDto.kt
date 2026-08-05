package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WordDto(
    val id: String,
    @SerialName("course_id") val courseId: String,
    val spelling: String,
    val syllables: List<String> = emptyList(),
    @SerialName("meaning_cn") val meaningCn: String? = null,
    val phonetic: String? = null,
    @SerialName("sample_sentence") val sampleSentence: String? = null,
    @SerialName("sample_sentence_translation") val sampleSentenceTranslation: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class WordScoreRequest(
    val score: Int,
)

@Serializable
data class WordScoreResponse(
    @SerialName("word_id") val wordId: String,
    @SerialName("lexeme_id") val lexemeId: String,
    val mastery: Float,
    val attempts: Int,
    @SerialName("passed_count") val passedCount: Int,
    @SerialName("best_score") val bestScore: Int,
    @SerialName("last_score") val lastScore: Int? = null,
)
