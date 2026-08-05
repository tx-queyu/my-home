package com.myhome.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.repo.FamilyRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberFormUiState(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val role: String = "child",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class MemberFormViewModel @Inject constructor(
    private val familyRepo: FamilyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MemberFormUiState())
    val ui: StateFlow<MemberFormUiState> = _ui.asStateFlow()

    fun onUsernameChange(v: String) = _ui.update { it.copy(username = v, error = null) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, error = null) }
    fun onDisplayNameChange(v: String) = _ui.update { it.copy(displayName = v, error = null) }
    fun onRoleChange(v: String) = _ui.update { it.copy(role = v, error = null) }

    fun save() {
        val s = _ui.value
        if (s.saving) return
        val u = s.username.trim()
        when {
            u.length < 3 -> {
                _ui.update { it.copy(error = "用户名至少 3 个字符") }; return
            }
            s.password.length < 6 -> {
                _ui.update { it.copy(error = "密码至少 6 位") }; return
            }
            s.displayName.isBlank() -> {
                _ui.update { it.copy(error = "请填写昵称") }; return
            }
        }
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                familyRepo.createMember(u, s.password, s.displayName.trim(), s.role)
            }.onSuccess {
                _ui.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                _ui.update { it.copy(saving = false, error = friendlyError(e)) }
            }
        }
    }
}
