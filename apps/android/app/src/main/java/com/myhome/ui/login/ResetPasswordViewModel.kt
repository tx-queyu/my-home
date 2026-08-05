package com.myhome.ui.login

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

data class ResetPasswordUiState(
    val target: String = "",
    val code: String = "",
    val newPassword: String = "",
    val loading: Boolean = false,
    val sending: Boolean = false,
    val cooldownRemaining: Int = 0,
    val error: String? = null,
    val toast: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val verificationRepo: VerificationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ResetPasswordUiState())
    val ui: StateFlow<ResetPasswordUiState> = _ui.asStateFlow()

    fun onTargetChange(v: String) = _ui.update { it.copy(target = v, error = null) }
    fun onCodeChange(v: String) = _ui.update { it.copy(code = v, error = null) }
    fun onNewPasswordChange(v: String) = _ui.update { it.copy(newPassword = v, error = null) }
    fun consumeToast() = _ui.update { it.copy(toast = null) }

    private fun isEmail(s: String) = s.contains("@")

    private fun normalizedTarget(): String {
        val t = _ui.value.target.trim()
        return if (isEmail(t)) t else if (t.startsWith("+")) t else "+86$t"
    }

    fun sendCode() {
        val s = _ui.value
        if (s.sending || s.cooldownRemaining > 0) return
        val target = s.target.trim()
        if (target.isEmpty()) {
            _ui.update { it.copy(error = "请输入手机号或邮箱") }; return
        }
        val channel = if (isEmail(target)) "email" else "sms"
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                verificationRepo.sendCode(
                    channel = channel,
                    target = normalizedTarget(),
                    purpose = "reset_password",
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
        val target = s.target.trim()
        val code = s.code.trim()
        if (target.isEmpty() || code.isEmpty() || s.newPassword.isEmpty()) {
            _ui.update { it.copy(error = "请填写完整信息") }; return
        }
        if (s.newPassword.length < 6) {
            _ui.update { it.copy(error = "新密码至少 6 位") }; return
        }
        val channel = if (isEmail(target)) "email" else "sms"
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val resp = verificationRepo.verifyCode(
                    channel = channel,
                    target = normalizedTarget(),
                    purpose = "reset_password",
                    code = code,
                )
                verificationRepo.resetPassword(resp.verifyToken, s.newPassword)
            }.onSuccess {
                _ui.update { it.copy(loading = false, done = true, toast = "密码已重置") }
                onDone()
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }
}
