package com.myhome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.ApplianceDto
import com.myhome.repo.ApplianceRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApplianceListUiState(
    val loading: Boolean = false,
    val items: List<ApplianceDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ApplianceViewModel @Inject constructor(
    private val repo: ApplianceRepository,
) : ViewModel() {

    private val _list = MutableStateFlow(ApplianceListUiState(loading = true))
    val list: StateFlow<ApplianceListUiState> = _list.asStateFlow()

    private val _detail = MutableStateFlow<ApplianceDto?>(null)
    val detail: StateFlow<ApplianceDto?> = _detail.asStateFlow()

    fun refresh() {
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.list() }
                .onSuccess { items -> _list.update { it.copy(loading = false, items = items) } }
                .onFailure { e -> _list.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun loadDetail(id: String) {
        viewModelScope.launch {
            runCatching { repo.get(id) }
                .onSuccess { _detail.value = it }
                .onFailure { e ->
                    _list.update { it.copy(error = friendlyError(e)) }
                }
        }
    }

    fun clearDetail() { _detail.value = null }

    fun save(
        id: String?,
        name: String,
        type: String,
        location: String,
        status: String,
        notes: String?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                if (id == null) {
                    repo.create(name, type, location, status, notes)
                } else {
                    repo.update(id, name, type, location, status, notes)
                }
            }
                .onSuccess { onDone() }
                .onFailure { e -> _list.update { it.copy(error = friendlyError(e)) } }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onSuccess { onDone() }
                .onFailure { e -> _list.update { it.copy(error = friendlyError(e)) } }
        }
    }
}
