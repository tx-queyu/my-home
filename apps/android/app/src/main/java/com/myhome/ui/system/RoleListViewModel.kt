package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.SystemRoleDto
import com.myhome.repo.SystemRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleListUiState(
    val loading: Boolean = true,
    val roles: List<SystemRoleDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class RoleListViewModel @Inject constructor(
    private val repo: SystemRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(RoleListUiState())
    val ui: StateFlow<RoleListUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.listRoles() }
                .onSuccess { roles -> _ui.update { it.copy(loading = false, roles = roles) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }
}
