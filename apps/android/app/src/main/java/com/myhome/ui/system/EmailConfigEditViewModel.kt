package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.EmailConfigDto
import com.myhome.repo.VerificationRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmailConfigEditUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val existing: EmailConfigDto? = null,
    val error: String? = null,
)

@HiltViewModel
class EmailConfigEditViewModel @Inject constructor(
    private val repo: VerificationRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(EmailConfigEditUiState())
    val ui: StateFlow<EmailConfigEditUiState> = _ui.asStateFlow()

    fun init(id: String?) {
        if (id == null) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.listEmailConfigs() }
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
        smtpHost: String,
        smtpPort: Int?,
        encryption: String,
        username: String,
        password: String,
        accessKeyId: String,
        accessKeySecret: String,
        region: String,
        fromEmail: String,
        fromName: String,
        dailyLimit: Int,
        intervalSeconds: Int,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repo.createEmailConfig(
                    provider = provider,
                    smtpHost = smtpHost.ifBlank { null },
                    smtpPort = smtpPort,
                    encryption = encryption.ifBlank { null },
                    username = username.ifBlank { null },
                    password = password.ifBlank { null },
                    accessKeyId = accessKeyId.ifBlank { null },
                    accessKeySecret = accessKeySecret.ifBlank { null },
                    region = region.ifBlank { null },
                    fromEmail = fromEmail.ifBlank { null },
                    fromName = fromName.ifBlank { null },
                    dailyLimit = dailyLimit,
                    intervalSeconds = intervalSeconds,
                )
            }.onSuccess { onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun update(
        id: String,
        smtpHost: String,
        smtpPort: Int?,
        encryption: String,
        username: String,
        password: String,
        accessKeyId: String,
        accessKeySecret: String,
        region: String,
        fromEmail: String,
        fromName: String,
        dailyLimit: Int,
        intervalSeconds: Int,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repo.updateEmailConfig(
                    id = id,
                    smtpHost = smtpHost.ifBlank { null },
                    smtpPort = smtpPort,
                    encryption = encryption.ifBlank { null },
                    username = username.ifBlank { null },
                    password = password.ifBlank { null },
                    accessKeyId = accessKeyId.ifBlank { null },
                    accessKeySecret = accessKeySecret.ifBlank { null },
                    region = region.ifBlank { null },
                    fromEmail = fromEmail.ifBlank { null },
                    fromName = fromName.ifBlank { null },
                    dailyLimit = dailyLimit,
                    intervalSeconds = intervalSeconds,
                )
            }.onSuccess { onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }
}
