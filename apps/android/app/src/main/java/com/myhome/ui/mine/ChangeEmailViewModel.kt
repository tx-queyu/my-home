package com.myhome.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.repo.VerificationRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangeEmailUiState(
    val newEmail: String = "",
    val code: String = "",
    val loading: Boolean = false,
    val sending: Boolean = false,
    val cooldownRemaining: Int = 0,
    val error: String? = null,
    val toast: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ChangeEmailViewModel @Inject constructor(
    private val verificationRepo: VerificationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChangeEmailUiState())
    val ui: StateFlow<ChangeEmailUiState> = _ui.asStateFlow()

    fun onNewEmailChange(v: String) = _ui.update { it.copy(newEmail = v, error = null) }
    fun onCodeChange(v: String) = _ui.update { it.copy(code = v, error = null) }
    fun consumeToast() = _ui.update { it.copy(toast = null) }

    fun sendCode() {
        val s = _ui.value
        if (s.sending || s.cooldownRemaining > 0) return
        val email = s.newEmail.trim()
        if (email.isEmpty() || !email.contains("@")) {
            _ui.update { it.copy(error = "请输入有效邮箱") }; return
        }
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                verificationRepo.sendCode(
                    channel = "email",
                    target = email,
                    purpose = "change_email",
                )
            }.onSuccess {
                _ui.update { it.copy(sending = false, cooldownRemaining = 60, toast = "验证码已发送") }
                startCooldown()
            }.onFailure { e ->
                _ui.update { it.copy(sending = false, error = friendlyError(e)) }
            }
        }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            while (_ui.value.cooldownRemaining > 0) {
                kotlinx.coroutines.delay(1000)
                _ui.update { it.copy(cooldownRemaining = (it.cooldownRemaining - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun submit(onDone: () -> Unit) {
        val s = _ui.value
        if (s.loading) return
        val email = s.newEmail.trim()
        val code = s.code.trim()
        if (email.isEmpty() || code.isEmpty()) {
            _ui.update { it.copy(error = "请填写邮箱和验证码") }; return
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val resp = verificationRepo.verifyCode(
                    channel = "email",
                    target = email,
                    purpose = "change_email",
                    code = code,
                )
                verificationRepo.changeEmail(resp.verifyToken, email)
            }.onSuccess {
                _ui.update { it.copy(loading = false, done = true, toast = "邮箱已更换") }
                onDone()
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }
}
