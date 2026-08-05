package com.myhome.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.DeviceDto
import com.myhome.repo.AuthRepository
import com.myhome.repo.DeviceRepository
import com.myhome.util.RoleUtil
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceDetailUiState(
    val loading: Boolean = true,
    val device: DeviceDto? = null,
    val isParent: Boolean = false,
    val isWorking: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val repo: DeviceRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DeviceDetailUiState())
    val ui: StateFlow<DeviceDetailUiState> = _ui.asStateFlow()

    fun load(deviceId: String) {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val isParent = runCatching { RoleUtil.canManageFamily(authRepo.me().roles) }.getOrDefault(false)
            runCatching { repo.get(deviceId) }
                .onSuccess { device ->
                    _ui.update {
                        it.copy(loading = false, device = device, isParent = isParent, isWorking = false)
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = friendlyError(e)) }
                }
        }
    }

    private fun reloadSilently(deviceId: String) {
        viewModelScope.launch {
            val isParent = runCatching { RoleUtil.canManageFamily(authRepo.me().roles) }.getOrDefault(false)
            runCatching { repo.get(deviceId) }
                .onSuccess { device ->
                    _ui.update { it.copy(device = device, isParent = isParent) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(error = friendlyError(e)) }
                }
        }
    }

    fun toggle(deviceId: String, currentBlocked: Boolean) {
        val device = _ui.value.device ?: return
        if (!_ui.value.isParent || !device.isDeviceOwner || _ui.value.isWorking) return
        _ui.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val commandType = if (currentBlocked) "disable_block" else "enable_block"
            runCatching { repo.issueCommand(deviceId, commandType) }
                .onSuccess {
                    _ui.update { it.copy(isWorking = false) }
                    delay(3000)
                    reloadSilently(deviceId)
                }
                .onFailure { e ->
                    _ui.update { it.copy(isWorking = false, error = friendlyError(e)) }
                }
        }
    }
}

