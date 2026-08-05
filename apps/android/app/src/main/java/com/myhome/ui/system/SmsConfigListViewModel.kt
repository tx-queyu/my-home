package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.SmsConfigDto
import com.myhome.net.dto.TestResultDto
import com.myhome.repo.VerificationRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmsConfigListUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val configs: List<SmsConfigDto> = emptyList(),
    val error: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class SmsConfigListViewModel @Inject constructor(
    private val repo: VerificationRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SmsConfigListUiState())
    val ui: StateFlow<SmsConfigListUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.listSmsConfigs() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, configs = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun activate(id: String) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.activateSmsConfig(id) }
                .onSuccess { refreshAfterMutation("已激活") }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun deactivate(id: String) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.deactivateSmsConfig(id) }
                .onSuccess { refreshAfterMutation("已停用") }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun delete(id: String) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.deleteSmsConfig(id) }
                .onSuccess { refreshAfterMutation("已删除") }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun test(id: String) {
        _ui.update { it.copy(saving = true, error = null, toast = null) }
        viewModelScope.launch {
            runCatching { repo.testSmsConfig(id) }
                .onSuccess { result: TestResultDto ->
                    val msg = if (result.ok) "探活通过" else "探活失败：${result.error ?: "未知错误"}"
                    _ui.update { it.copy(saving = false, toast = msg) }
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    private fun refreshAfterMutation(toast: String) {
        _ui.update { it.copy(saving = false, toast = toast) }
        refresh()
    }
}
