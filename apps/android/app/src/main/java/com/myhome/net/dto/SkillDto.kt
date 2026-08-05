package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillOverviewDto(
    @SerialName("total_words") val totalWords: Int,
    @SerialName("assessed_words") val assessedWords: Int,
    @SerialName("mastered_words") val masteredWords: Int,
    @SerialName("average_mastery") val averageMastery: Float,
    val coverage: Float,
    @SerialName("mastered_coverage") val masteredCoverage: Float,
    @SerialName("by_state") val byState: Map<String, Int> = emptyMap(),
)

@Serializable
data class TextbookCoverageDto(
    val subject: String,
    val textbook: String,
    @SerialName("learning_methods") val learningMethods: List<String> = emptyList(),
    @SerialName("total_words") val totalWords: Int,
    @SerialName("touched_words") val touchedWords: Int,
    @SerialName("mastered_words") val masteredWords: Int,
    @SerialName("touched_coverage") val touchedCoverage: Float,
    @SerialName("mastered_coverage") val masteredCoverage: Float,
    @SerialName("is_completed") val isCompleted: Boolean,
) {
    /** UI 选中态 key(教材无单一 id,用 subject|textbook 复合键)。 */
    val key: String get() = "$subject|$textbook"
}

@Serializable
data class ChildWordMasteryDto(
    @SerialName("lexeme_id") val lexemeId: String,
    val spelling: String,
    @SerialName("meaning_cn") val meaningCn: String? = null,
    val phonetic: String? = null,
    val mastery: Float,
    val attempts: Int,
    @SerialName("passed_count") val passedCount: Int,
    @SerialName("best_score") val bestScore: Int,
    @SerialName("last_score") val lastScore: Int? = null,
    @SerialName("last_assessed_at") val lastAssessedAt: String? = null,
    val state: String,  // new | learning | familiar | mastered
)
