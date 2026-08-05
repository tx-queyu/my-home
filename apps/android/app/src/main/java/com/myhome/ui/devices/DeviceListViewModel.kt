package com.myhome.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.DeviceDto
import com.myhome.repo.AuthRepository
import com.myhome.repo.DeviceRepository
import com.myhome.util.RoleUtil
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceListUiState(
    val loading: Boolean = true,
    val devices: List<DeviceDto> = emptyList(),
    val isParent: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val repo: DeviceRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DeviceListUiState())
    val ui: StateFlow<DeviceListUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val isParent = runCatching { RoleUtil.canManageFamily(authRepo.me().roles) }.getOrDefault(false)
            runCatching { repo.list() }
                .onSuccess { devices ->
                    _ui.update {
                        it.copy(loading = false, devices = devices, isParent = isParent)
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = friendlyError(e)) }
                }
        }
    }
}
