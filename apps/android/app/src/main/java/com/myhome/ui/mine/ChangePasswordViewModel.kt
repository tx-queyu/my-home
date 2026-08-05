package com.myhome.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.repo.AuthRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val saving: Boolean = false,
    val toast: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChangePasswordUiState())
    val ui: StateFlow<ChangePasswordUiState> = _ui.asStateFlow()

    fun onCurrentChange(v: String) {
        _ui.update { it.copy(currentPassword = v, error = null) }
    }

    fun onNewChange(v: String) {
        _ui.update { it.copy(newPassword = v, error = null) }
    }

    fun onConfirmChange(v: String) {
        _ui.update { it.copy(confirmPassword = v, error = null) }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    fun submit(onDone: () -> Unit) {
        val s = _ui.value
        when {
            s.currentPassword.length < 6 ->
                _ui.update { it.copy(error = "当前密码至少 6 位") }
            s.newPassword.length < 6 ->
                _ui.update { it.copy(error = "新密码至少 6 位") }
            s.newPassword != s.confirmPassword ->
                _ui.update { it.copy(error = "两次输入的新密码不一致") }
            s.newPassword == s.currentPassword ->
                _ui.update { it.copy(error = "新密码不能与当前密码相同") }
            else -> {
                _ui.update { it.copy(saving = true, error = null) }
                viewModelScope.launch {
                    runCatching {
                        authRepo.changePassword(s.currentPassword, s.newPassword)
                    }
                        .onSuccess {
                            _ui.update {
                                it.copy(saving = false, toast = "密码已更新")
                            }
                            onDone()
                        }
                        .onFailure { e ->
                            _ui.update {
                                it.copy(saving = false, error = friendlyError(e))
                            }
                        }
                }
            }
        }
    }
}
