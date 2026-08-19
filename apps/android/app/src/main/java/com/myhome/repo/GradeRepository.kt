package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.GradeCreateRequest
import com.myhome.net.dto.GradeDto
import com.myhome.net.dto.GradeUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

/** 学科成绩（v0.17.0）：家长录入/管理，全家可查。 */
@Singleton
class GradeRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun list(userId: String? = null, subject: String? = null): List<GradeDto> =
        api.listGrades(userId, subject)

    suspend fun create(req: GradeCreateRequest): GradeDto = api.createGrade(req)

    suspend fun update(id: String, req: GradeUpdateRequest): GradeDto = api.updateGrade(id, req)

    suspend fun delete(id: String) = api.deleteGrade(id)
}
