package com.myhome.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.admin.DeviceControlManager
import com.myhome.repo.AuthRepository
import com.myhome.util.RoleUtil
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceControlUiState(
    val loading: Boolean = true,
    val isParent: Boolean = false,
    val isDeviceOwner: Boolean = false,
    val isBlocked: Boolean = false,
    val isWorking: Boolean = false,
    val error: String? = null,
    val showRemoveConfirm: Boolean = false,
)

@HiltViewModel
class DeviceControlViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val manager: DeviceControlManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(DeviceControlUiState())
    val ui: StateFlow<DeviceControlUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val isParent = runCatching { RoleUtil.canManageFamily(authRepo.me().roles) }.getOrDefault(false)
            val isOwner = manager.isDeviceOwner()
            val isBlocked = manager.isUninstallBlocked()
            _ui.update {
                it.copy(
                    loading = false,
                    isParent = isParent,
                    isDeviceOwner = isOwner,
                    isBlocked = isBlocked,
                )
            }
        }
    }

    fun toggle() {
        val current = _ui.value
        if (!current.isParent || !current.isDeviceOwner || current.isWorking) return
        _ui.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                val targetBlocked = !current.isBlocked
                val ok = manager.setUninstallBlocked(targetBlocked)
                if (!ok) {
                    _ui.update {
                        it.copy(
                            isWorking = false,
                            error = if (targetBlocked) "无法开启卸载锁定" else "无法关闭卸载锁定",
                        )
                    }
                    return@launch
                }
                _ui.update { it.copy(isWorking = false) }
                refresh()
            } catch (t: Throwable) {
                _ui.update { it.copy(isWorking = false, error = friendlyError(t)) }
            }
        }
    }

    fun showRemoveConfirm() {
        _ui.update { it.copy(showRemoveConfirm = true) }
    }

    fun dismissRemoveConfirm() {
        _ui.update { it.copy(showRemoveConfirm = false) }
    }

    fun removeDeviceOwner() {
        val current = _ui.value
        if (!current.isParent || !current.isDeviceOwner || current.isWorking) return
        _ui.update { it.copy(isWorking = true, showRemoveConfirm = false, error = null) }
        viewModelScope.launch {
            try {
                manager.setUninstallBlocked(false)
                val ok = manager.clearDeviceOwner()
                if (!ok) {
                    _ui.update {
                        it.copy(
                            isWorking = false,
                            error = "移除 Device Owner 失败，请稍后重试",
                        )
                    }
                    return@launch
                }
                _ui.update { it.copy(isWorking = false) }
                refresh()
            } catch (t: Throwable) {
                _ui.update { it.copy(isWorking = false, error = friendlyError(t)) }
            }
        }
    }
}
