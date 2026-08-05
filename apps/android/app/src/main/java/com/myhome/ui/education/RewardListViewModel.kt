package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.RewardDto
import com.myhome.repo.AuthRepository
import com.myhome.repo.RewardRepository
import com.myhome.util.RoleUtil
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RewardListUiState(
    val loading: Boolean = false,
    val items: List<RewardDto> = emptyList(),
    val balance: Int = 0,
    val isParent: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RewardListViewModel @Inject constructor(
    private val repo: RewardRepository,
    private val authRepo: AuthRepository,
    private val pointRepo: com.myhome.repo.PointRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(RewardListUiState(loading = true))
    val ui: StateFlow<RewardListUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val roles = runCatching { authRepo.me().roles }.getOrNull().orEmpty()
            val isParent = RoleUtil.canManageFamily(roles)
            runCatching { repo.listRewards() }
                .onSuccess { rewards ->
                    val balance = runCatching { pointRepo.me().balance }.getOrNull() ?: 0
                    _ui.update {
                        it.copy(loading = false, items = rewards, balance = balance, isParent = isParent)
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun redeem(rewardId: String) {
        viewModelScope.launch {
            runCatching { repo.redeem(rewardId) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteReward(id) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }
}
