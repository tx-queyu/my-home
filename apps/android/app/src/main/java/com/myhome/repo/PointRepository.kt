package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.FamilyPointAccountDto
import com.myhome.net.dto.PointMeDto
import com.myhome.net.dto.PointTransactionDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PointRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun me(): PointMeDto = api.getMyPoints()
    suspend fun transactions(limit: Int = 20, offset: Int = 0): List<PointTransactionDto> =
        api.listPointTransactions(limit, offset)
    suspend fun listFamilyAccounts(): List<FamilyPointAccountDto> = api.listFamilyPointAccounts()
}
