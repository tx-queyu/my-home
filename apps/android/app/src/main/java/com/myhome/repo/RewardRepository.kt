package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.RedeemRequest
import com.myhome.net.dto.RedemptionDto
import com.myhome.net.dto.RewardDto
import com.myhome.net.dto.RewardRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun listRewards(includeInactive: Boolean = false): List<RewardDto> = api.listRewards(includeInactive)
    suspend fun createReward(req: RewardRequest): RewardDto = api.createReward(req)
    suspend fun updateReward(id: String, req: RewardRequest): RewardDto = api.updateReward(id, req)
    suspend fun deleteReward(id: String) = api.deleteReward(id)

    suspend fun listRedemptions(status: String? = null, userId: String? = null): List<RedemptionDto> =
        api.listRedemptions(status, userId)
    suspend fun redeem(rewardId: String): RedemptionDto = api.createRedemption(RedeemRequest(rewardId))
    suspend fun fulfill(id: String): RedemptionDto = api.fulfillRedemption(id)
    suspend fun reject(id: String): RedemptionDto = api.rejectRedemption(id)
}
