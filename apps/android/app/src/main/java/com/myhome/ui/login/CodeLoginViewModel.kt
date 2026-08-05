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

data class CodeLoginUiState(
    val phone: String = "",
    val code: String = "",
    val loading: Boolean = false,
    val sending: Boolean = false,
    val cooldownRemaining: Int = 0, // seconds; 0 = can resend
    val verifyToken: String? = null,
    val error: String? = null,
    val toast: String? = null,
    val loggedInUser: com.myhome.net.dto.UserInfo? = null,
)

@HiltViewModel
class CodeLoginViewModel @Inject constructor(
    private val verificationRepo: VerificationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CodeLoginUiState())
    val ui: StateFlow<CodeLoginUiState> = _ui.asStateFlow()

    fun onPhoneChange(v: String) = _ui.update { it.copy(phone = v, error = null) }
    fun onCodeChange(v: String) = _ui.update { it.copy(code = v, error = null) }
    fun consumeToast() = _ui.update { it.copy(toast = null) }

    fun sendCode() {
        val s = _ui.value
        if (s.sending || s.cooldownRemaining > 0) return
        val phone = s.phone.trim()
        if (phone.isEmpty()) {
            _ui.update { it.copy(error = "请输入手机号") }; return
        }
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                verificationRepo.sendCode(
                    channel = "sms",
                    target = normalizePhone(phone),
                    purpose = "login_by_code",
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

    fun login(onDone: () -> Unit) {
        val s = _ui.value
        if (s.loading) return
        val phone = s.phone.trim()
        val code = s.code.trim()
        if (phone.isEmpty() || code.isEmpty()) {
            _ui.update { it.copy(error = "请输入手机号和验证码") }; return
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val resp = verificationRepo.verifyCode(
                    channel = "sms",
                    target = normalizePhone(phone),
                    purpose = "login_by_code",
                    code = code,
                )
                verificationRepo.loginByCode(resp.verifyToken)
            }.onSuccess { user ->
                _ui.update { it.copy(loading = false, loggedInUser = user) }
                onDone()
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    private fun normalizePhone(p: String): String {
        // 简化：用户输入 13... 自动加 +86；以 + 开头视为国际号原样
        return if (p.startsWith("+")) p else "+86$p"
    }
}
