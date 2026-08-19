package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.HabitCreateRequest
import com.myhome.net.dto.HabitDto
import com.myhome.net.dto.HabitLogDto
import com.myhome.net.dto.HabitUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

/** 习惯打卡（v0.17.0）：习惯 CRUD + 每日打卡 + 日志。 */
@Singleton
class HabitRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun list(includeInactive: Boolean = false): List<HabitDto> = api.listHabits(includeInactive)

    suspend fun create(req: HabitCreateRequest): HabitDto = api.createHabit(req)

    suspend fun update(id: String, req: HabitUpdateRequest): HabitDto = api.updateHabit(id, req)

    suspend fun delete(id: String) = api.deleteHabit(id)

    suspend fun checkIn(id: String): HabitLogDto = api.checkInHabit(id)

    suspend fun logs(
        userId: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
    ): List<HabitLogDto> = api.listHabitLogs(userId, dateFrom, dateTo)
}
