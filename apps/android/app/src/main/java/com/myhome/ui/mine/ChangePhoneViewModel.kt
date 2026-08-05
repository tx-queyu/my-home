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

data class ChangePhoneUiState(
    val newPhone: String = "",
    val code: String = "",
    val loading: Boolean = false,
    val sending: Boolean = false,
    val cooldownRemaining: Int = 0,
    val error: String? = null,
    val toast: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ChangePhoneViewModel @Inject constructor(
    private val verificationRepo: VerificationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChangePhoneUiState())
    val ui: StateFlow<ChangePhoneUiState> = _ui.asStateFlow()

    fun onNewPhoneChange(v: String) = _ui.update { it.copy(newPhone = v, error = null) }
    fun onCodeChange(v: String) = _ui.update { it.copy(code = v, error = null) }
    fun consumeToast() = _ui.update { it.copy(toast = null) }

    private fun normalizedTarget(): String {
        val t = _ui.value.newPhone.trim()
        return if (t.startsWith("+")) t else "+86$t"
    }

    fun sendCode() {
        val s = _ui.value
        if (s.sending || s.cooldownRemaining > 0) return
        if (s.newPhone.trim().isEmpty()) {
            _ui.update { it.copy(error = "请输入新手机号") }; return
        }
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                verificationRepo.sendCode(
                    channel = "sms",
                    target = normalizedTarget(),
                    purpose = "change_phone",
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
        val phone = s.newPhone.trim()
        val code = s.code.trim()
        if (phone.isEmpty() || code.isEmpty()) {
            _ui.update { it.copy(error = "请填写手机号和验证码") }; return
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val resp = verificationRepo.verifyCode(
                    channel = "sms",
                    target = normalizedTarget(),
                    purpose = "change_phone",
                    code = code,
                )
                verificationRepo.changePhone(resp.verifyToken, normalizedTarget())
            }.onSuccess {
                _ui.update { it.copy(loading = false, done = true, toast = "手机号已更换") }
                onDone()
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }
}
