package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.ChildWordMasteryDto
import com.myhome.net.dto.SkillOverviewDto
import com.myhome.net.dto.TextbookCoverageDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun myOverview(): SkillOverviewDto = api.getMySkillOverview()

    suspend fun myTextbooks(): List<TextbookCoverageDto> = api.listMyTextbookCoverage()

    suspend fun myWords(
        state: String? = null,
        subject: String? = null,
        textbook: String? = null,
    ): List<ChildWordMasteryDto> = api.listMyWordMastery(state, subject, textbook)

    suspend fun childOverview(childId: String): SkillOverviewDto =
        api.getChildSkillOverview(childId)

    suspend fun childTextbooks(childId: String): List<TextbookCoverageDto> =
        api.listChildTextbookCoverage(childId)

    suspend fun childWords(
        childId: String,
        state: String? = null,
        subject: String? = null,
        textbook: String? = null,
    ): List<ChildWordMasteryDto> = api.listChildWordMastery(childId, state, subject, textbook)
}
