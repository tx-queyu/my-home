package com.myhome.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.admin.AdbActivator
import com.myhome.admin.ActivationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceOwnerSetupUiState(
    val hostPort: String = "",
    val pairingCode: String = "",
    val isWorking: Boolean = false,
    val result: ActivationResult? = null,
)

@HiltViewModel
class DeviceOwnerSetupViewModel @Inject constructor(
    app: Application,
) : AndroidViewModel(app) {

    private val activator = AdbActivator(app)

    private val _ui = MutableStateFlow(DeviceOwnerSetupUiState())
    val ui: StateFlow<DeviceOwnerSetupUiState> = _ui.asStateFlow()

    fun onHostPortChange(v: String) {
        _ui.update { it.copy(hostPort = v.trim(), result = null) }
    }

    fun onPairingCodeChange(v: String) {
        val digits = v.filter { c -> c.isDigit() }.take(6)
        _ui.update { it.copy(pairingCode = digits, result = null) }
    }

    fun activate() {
        val s = _ui.value
        if (s.isWorking) return
        val parsed = parseHostPort(s.hostPort)
        if (parsed == null || s.pairingCode.length != 6) {
            _ui.update {
                it.copy(result = ActivationResult(
                    ok = false,
                    message = "请输入合法的 IP:端口（如 192.168.1.100:4321）和 6 位配对码。",
                    detailCode = "adb_invalid_input",
                ))
            }
            return
        }
        _ui.update { it.copy(isWorking = true, result = null) }
        viewModelScope.launch {
            val r = activator.pairAndActivate(parsed.first, parsed.second, s.pairingCode)
            _ui.update { it.copy(isWorking = false, result = r) }
        }
    }

    private fun parseHostPort(raw: String): Pair<String, Int>? {
        val s = raw.trim()
        val idx = s.lastIndexOf(':')
        if (idx <= 0 || idx == s.length - 1) return null
        val host = s.substring(0, idx).trim().trim('[', ']')
        val port = s.substring(idx + 1).trim().toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        if (host.isEmpty()) return null
        return host to port
    }
}
