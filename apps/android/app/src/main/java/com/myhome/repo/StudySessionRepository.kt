package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.StudySessionDto
import com.myhome.net.dto.StudySessionReportRequest
import com.myhome.net.dto.StudyStatsDto
import javax.inject.Inject
import javax.inject.Singleton

/** 学习时长（v0.17.0）：session 完成自动上报 + 聚合统计。 */
@Singleton
class StudySessionRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun report(req: StudySessionReportRequest): StudySessionDto = api.reportStudySession(req)

    suspend fun list(
        userId: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
    ): List<StudySessionDto> = api.listStudySessions(userId, dateFrom, dateTo)

    suspend fun stats(userId: String? = null): StudyStatsDto = api.getStudyStats(userId)
}
