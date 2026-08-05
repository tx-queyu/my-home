package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.SmsConfigDto
import com.myhome.repo.VerificationRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmsConfigEditUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val existing: SmsConfigDto? = null,
    val error: String? = null,
)

@HiltViewModel
class SmsConfigEditViewModel @Inject constructor(
    private val repo: VerificationRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SmsConfigEditUiState())
    val ui: StateFlow<SmsConfigEditUiState> = _ui.asStateFlow()

    fun init(id: String?) {
        if (id == null) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.listSmsConfigs() }
                .onSuccess { list ->
                    val found = list.firstOrNull { it.id == id }
                    _ui.update { it.copy(loading = false, existing = found) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = friendlyError(e)) }
                }
        }
    }

    fun create(
        provider: String,
        signName: String,
        templateCode: String,
        accessKeyId: String,
        accessKeySecret: String,
        sdkAppId: String,
        region: String,
        dailyLimit: Int,
        intervalSeconds: Int,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repo.createSmsConfig(
                    provider = provider,
                    signName = signName.ifBlank { null },
                    templateCode = templateCode.ifBlank { null },
                    accessKeyId = accessKeyId.ifBlank { null },
                    accessKeySecret = accessKeySecret.ifBlank { null },
                    sdkAppId = sdkAppId.ifBlank { null },
                    region = region.ifBlank { null },
                    dailyLimit = dailyLimit,
                    intervalSeconds = intervalSeconds,
                )
            }.onSuccess { onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun update(
        id: String,
        signName: String,
        templateCode: String,
        accessKeyId: String,
        accessKeySecret: String,
        sdkAppId: String,
        region: String,
        dailyLimit: Int,
        intervalSeconds: Int,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repo.updateSmsConfig(
                    id = id,
                    signName = signName.ifBlank { null },
                    templateCode = templateCode.ifBlank { null },
                    accessKeyId = accessKeyId.ifBlank { null },
                    accessKeySecret = accessKeySecret.ifBlank { null },
                    sdkAppId = sdkAppId.ifBlank { null },
                    region = region.ifBlank { null },
                    dailyLimit = dailyLimit,
                    intervalSeconds = intervalSeconds,
                )
            }.onSuccess { onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }
}
