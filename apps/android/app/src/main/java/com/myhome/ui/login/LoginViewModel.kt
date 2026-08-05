package com.myhome.ui.login

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

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val familyName: String = "",
    val mode: LoginMode = LoginMode.LOGIN,
    val loading: Boolean = false,
    val error: String? = null,
    val loggedInUser: com.myhome.net.dto.UserInfo? = null,
)

enum class LoginMode { LOGIN, REGISTER }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun onUsernameChange(v: String) = _ui.update { it.copy(username = v, error = null) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, error = null) }
    fun onDisplayNameChange(v: String) = _ui.update { it.copy(displayName = v, error = null) }
    fun onFamilyNameChange(v: String) = _ui.update { it.copy(familyName = v, error = null) }
    fun onModeChange(mode: LoginMode) = _ui.update { it.copy(mode = mode, error = null) }

    fun submit() {
        val s = _ui.value
        if (s.loading) return
        when (s.mode) {
            LoginMode.LOGIN -> {
                val u = s.username.trim()
                if (u.isEmpty() || s.password.isEmpty()) {
                    _ui.update { it.copy(error = "请输入用户名和密码") }; return
                }
                _ui.update { it.copy(loading = true, error = null) }
                viewModelScope.launch {
                    runCatching { authRepo.login(u, s.password) }
                        .onSuccess { user -> _ui.update { it.copy(loading = false, loggedInUser = user) } }
                        .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
                }
            }
            LoginMode.REGISTER -> {
                val u = s.username.trim()
                if (u.isEmpty() || u.length < 3) {
                    _ui.update { it.copy(error = "用户名至少 3 个字符") }; return
                }
                if (s.password.length < 6) {
                    _ui.update { it.copy(error = "密码至少 6 位") }; return
                }
                if (s.displayName.isBlank() || s.familyName.isBlank()) {
                    _ui.update { it.copy(error = "请填写昵称和家庭名称") }; return
                }
                _ui.update { it.copy(loading = true, error = null) }
                viewModelScope.launch {
                    runCatching {
                        authRepo.register(u, s.password, s.displayName.trim(), s.familyName.trim())
                    }
                        .onSuccess { user -> _ui.update { it.copy(loading = false, loggedInUser = user) } }
                        .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
                }
            }
        }
    }
}
