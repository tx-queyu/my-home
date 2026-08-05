package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.RewardDto
import com.myhome.repo.RewardRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RewardFormUiState(
    val loading: Boolean = false,
    val reward: RewardDto? = null,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RewardFormViewModel @Inject constructor(
    private val repo: RewardRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(RewardFormUiState())
    val ui: StateFlow<RewardFormUiState> = _ui.asStateFlow()

    fun load(id: String?) {
        if (id == null) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.listRewards(includeInactive = true) }
                .onSuccess { list ->
                    val r = list.firstOrNull { it.id == id }
                    if (r != null) _ui.update { it.copy(loading = false, reward = r) }
                    else _ui.update { it.copy(loading = false, error = "奖励不存在") }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun save(
        id: String?,
        name: String,
        description: String?,
        cost: Int,
        stock: Int?,
        isActive: Boolean,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val req = com.myhome.net.dto.RewardRequest(
                name = name,
                description = description,
                cost = cost,
                stock = stock,
                isActive = isActive,
            )
            runCatching {
                if (id == null) repo.createReward(req) else repo.updateReward(id, req)
            }
                .onSuccess { onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }
}
