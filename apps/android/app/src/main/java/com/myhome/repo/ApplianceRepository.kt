package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.ApplianceDto
import com.myhome.net.dto.ApplianceRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplianceRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun list(): List<ApplianceDto> = api.listAppliances()

    suspend fun get(id: String): ApplianceDto = api.getAppliance(id)

    suspend fun create(
        name: String,
        type: String,
        location: String,
        status: String = "normal",
        notes: String? = null,
    ): ApplianceDto = api.createAppliance(ApplianceRequest(name, type, location, status, notes))

    suspend fun update(
        id: String,
        name: String,
        type: String,
        location: String,
        status: String = "normal",
        notes: String? = null,
    ): ApplianceDto = api.updateAppliance(id, ApplianceRequest(name, type, location, status, notes))

    suspend fun delete(id: String) = api.deleteAppliance(id)
}
