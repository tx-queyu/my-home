package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.RedemptionDto
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

data class RedemptionListUiState(
    val loading: Boolean = false,
    val items: List<RedemptionDto> = emptyList(),
    val isParent: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RedemptionListViewModel @Inject constructor(
    private val repo: RewardRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(RedemptionListUiState(loading = true))
    val ui: StateFlow<RedemptionListUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val roles = runCatching { authRepo.me().roles }.getOrNull().orEmpty()
            val isParent = RoleUtil.canManageFamily(roles)
            runCatching {
                // 家长看全家（不传 user_id）；孩子看自己
                repo.listRedemptions()
            }
                .onSuccess { items ->
                    _ui.update {
                        it.copy(loading = false, items = items, isParent = isParent)
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun fulfill(id: String) {
        viewModelScope.launch {
            runCatching { repo.fulfill(id) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }

    fun reject(id: String) {
        viewModelScope.launch {
            runCatching { repo.reject(id) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }
}
