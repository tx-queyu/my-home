package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.SystemFamilyDto
import com.myhome.net.dto.SystemRoleDto
import com.myhome.net.dto.SystemUserDto
import com.myhome.repo.SystemRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserEditUiState(
    val loading: Boolean = true,
    val user: SystemUserDto? = null,
    val families: List<SystemFamilyDto> = emptyList(),
    val roleOptions: List<SystemRoleDto> = emptyList(),
    val roleGroups: Map<String, String> = emptyMap(),  // role -> exclusive_group
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val roles: Set<String> = setOf("child"),
    val familyId: String? = null,
    val isActive: Boolean = true,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val resetting: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val saved: Boolean = false,
) {
    val isCreate: Boolean get() = user == null && !loading
}

@HiltViewModel
class UserEditViewModel @Inject constructor(
    private val repo: SystemRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(UserEditUiState())
    val ui: StateFlow<UserEditUiState> = _ui.asStateFlow()

    fun load(userId: String) {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val families = repo.listFamilies()
                val roleDtos = runCatching { repo.listRoles() }.getOrDefault(emptyList())
                val roleGroups = roleDtos
                    .mapNotNull { dto -> dto.exclusiveGroup?.let { dto.role to it } }
                    .toMap()
                if (userId.isBlank()) {
                    // 新建模式：默认选中第一个家庭（如果有）
                    _ui.update {
                        it.copy(
                            loading = false,
                            families = families,
                            roleOptions = roleDtos,
                            roleGroups = roleGroups,
                            familyId = families.firstOrNull()?.id,
                        )
                    }
                    return@runCatching
                }
                val users = repo.listAllUsers()
                val user = users.firstOrNull { it.id == userId }
                if (user == null) {
                    _ui.update { it.copy(loading = false, error = "用户不存在") }
                    return@runCatching
                }
                _ui.update {
                    it.copy(
                        loading = false,
                        user = user,
                        families = families,
                        roleOptions = roleDtos,
                        roleGroups = roleGroups,
                        username = user.username,
                        displayName = user.displayName,
                        roles = user.roles.toSet().ifEmpty { setOf("child") },
                        familyId = user.familyId,
                        isActive = user.isActive,
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun onUsernameChange(v: String) {
        _ui.update { it.copy(username = v) }
    }

    fun onPasswordChange(v: String) {
        _ui.update { it.copy(password = v) }
    }

    fun onDisplayNameChange(v: String) {
        _ui.update { it.copy(displayName = v) }
    }

    fun onRoleToggle(role: String) {
        _ui.update { current ->
            var newRoles = if (role in current.roles) {
                current.roles - role
            } else {
                current.roles + role
            }
            // 新选的角色若属于某个 exclusive_group，把同组其他角色移除（动态读后端配置）
            if (role in newRoles) {
                val group = current.roleGroups[role]
                if (group != null) {
                    newRoles = newRoles.filterTo(mutableSetOf()) { r ->
                        r == role || current.roleGroups[r] != group
                    }
                }
            }
            // 允许空角色（配合 familyId=null 表示无家庭用户）
            val hasFamilyRole = newRoles.any { it != "admin" }
            val newFamilyId = if (hasFamilyRole) {
                current.familyId ?: current.families.firstOrNull()?.id
            } else {
                null
            }
            current.copy(roles = newRoles, familyId = newFamilyId)
        }
    }

    fun onFamilyChange(familyId: String) {
        _ui.update { it.copy(familyId = familyId) }
    }

    fun onActiveChange(active: Boolean) {
        _ui.update { it.copy(isActive = active) }
    }

    fun save() {
        val s = _ui.value
        if (s.saving) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (s.user == null) {
                    // 新建
                    repo.createUser(
                        username = s.username.trim(),
                        password = s.password,
                        displayName = s.displayName.trim(),
                        roles = s.roles.toList(),
                        familyId = s.familyId,
                        isActive = s.isActive,
                    )
                } else {
                    // 编辑
                    repo.updateUser(
                        userId = s.user.id,
                        roles = s.roles.toList(),
                        familyId = s.familyId,
                        isActive = s.isActive,
                    )
                }
            }.onSuccess {
                _ui.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                _ui.update { it.copy(saving = false, error = friendlyError(e)) }
            }
        }
    }

    fun delete() {
        val s = _ui.value
        if (s.user == null || s.deleting) return
        _ui.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repo.deleteUser(s.user.id)
            }.onSuccess {
                _ui.update { it.copy(deleting = false, saved = true) }
            }.onFailure { e ->
                _ui.update { it.copy(deleting = false, error = friendlyError(e)) }
            }
        }
    }

    fun resetPassword(newPassword: String) {
        val s = _ui.value
        val userId = s.user?.id ?: return
        if (newPassword.length < 6) {
            _ui.update { it.copy(error = "新密码至少 6 位") }
            return
        }
        if (s.resetting) return
        _ui.update { it.copy(resetting = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.resetUserPassword(userId, newPassword) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            resetting = false,
                            toast = "已重置 ${s.user.displayName} 的密码",
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(resetting = false, error = friendlyError(e)) }
                }
        }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }
}
