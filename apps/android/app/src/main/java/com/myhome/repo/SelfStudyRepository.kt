package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.SelfStudyTextbookCreateRequest
import com.myhome.net.dto.SelfStudyTextbookDto
import com.myhome.net.dto.TextbookOptionDto
import javax.inject.Inject
import javax.inject.Singleton

/** 家长自学教材（v0.16.1）：教材清单 + 添加。 */
@Singleton
class SelfStudyRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun listMyTextbooks(): List<SelfStudyTextbookDto> = api.listMyTextbooks()

    suspend fun listAvailableTextbooks(): List<TextbookOptionDto> = api.listAvailableTextbooks()

    suspend fun addTextbook(subject: String, textbook: String): SelfStudyTextbookDto =
        api.addTextbook(SelfStudyTextbookCreateRequest(subject, textbook))
}
