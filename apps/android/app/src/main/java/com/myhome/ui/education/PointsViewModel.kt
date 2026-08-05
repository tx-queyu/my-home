package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.PointTransactionDto
import com.myhome.repo.PointRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PointsUiState(
    val loading: Boolean = false,
    val balance: Int = 0,
    val transactions: List<PointTransactionDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class PointsViewModel @Inject constructor(
    private val repo: PointRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PointsUiState(loading = true))
    val ui: StateFlow<PointsUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.me() }
                .onSuccess { me ->
                    _ui.update { it.copy(loading = false, balance = me.balance, transactions = me.recent) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun loadMore(offset: Int) {
        viewModelScope.launch {
            runCatching { repo.transactions(limit = 20, offset = offset) }
                .onSuccess { items -> _ui.update { it.copy(transactions = it.transactions + items) } }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }
}
