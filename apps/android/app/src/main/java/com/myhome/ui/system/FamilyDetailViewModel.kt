package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.SystemFamilyDetailDto
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

data class FamilyDetailUiState(
    val loading: Boolean = true,
    val family: SystemFamilyDetailDto? = null,
    val error: String? = null,
    val showAddMemberDialog: Boolean = false,
    val availableUsers: List<SystemUserDto> = emptyList(),
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class FamilyDetailViewModel @Inject constructor(
    private val repo: SystemRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FamilyDetailUiState())
    val ui: StateFlow<FamilyDetailUiState> = _ui.asStateFlow()

    fun load(familyId: String) {
        if (_ui.value.family?.id == familyId && !_ui.value.loading) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.getFamily(familyId) }
                .onSuccess { fam -> _ui.update { it.copy(loading = false, family = fam) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun refresh() {
        val id = _ui.value.family?.id ?: return
        _ui.update { it.copy(family = null) }  // 强制重拉
        load(id)
    }

    fun openAddMemberDialog() {
        _ui.update { it.copy(showAddMemberDialog = true, actionError = null) }
        viewModelScope.launch {
            runCatching { repo.listAllUsers() }
                .onSuccess { users ->
                    // 可选用户：无家庭 且 不是纯 admin（admin 不属于家庭）
                    val available = users.filter { u ->
                        u.familyId == null && u.roles.none { it == "admin" }
                    }
                    _ui.update { it.copy(availableUsers = available) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(actionError = friendlyError(e)) }
                }
        }
    }

    fun closeAddMemberDialog() {
        _ui.update { it.copy(showAddMemberDialog = false, actionError = null) }
    }

    fun addMember(userId: String) {
        val family = _ui.value.family ?: return
        if (_ui.value.actionInProgress) return
        _ui.update { it.copy(actionInProgress = true, actionError = null) }
        viewModelScope.launch {
            runCatching {
                val users = repo.listAllUsers()
                val target = users.firstOrNull { it.id == userId }
                    ?: error("user_not_found")
                // 加入家庭：roles 空时默认补 child，否则保留
                val newRoles = target.roles.ifEmpty { listOf("child") }
                repo.updateUser(
                    userId = userId,
                    roles = newRoles,
                    familyId = family.id,
                    isActive = target.isActive,
                )
            }.onSuccess {
                _ui.update { it.copy(actionInProgress = false, showAddMemberDialog = false) }
                refresh()
            }.onFailure { e ->
                _ui.update { it.copy(actionInProgress = false, actionError = friendlyError(e)) }
            }
        }
    }

    fun removeMember(userId: String) {
        if (_ui.value.actionInProgress) return
        _ui.update { it.copy(actionInProgress = true, actionError = null) }
        viewModelScope.launch {
            runCatching {
                val users = repo.listAllUsers()
                val target = users.firstOrNull { it.id == userId }
                    ?: error("user_not_found")
                // 移出家庭：roles 清空 + family_id 置 null
                repo.updateUser(
                    userId = userId,
                    roles = emptyList(),
                    familyId = null,
                    isActive = target.isActive,
                )
            }.onSuccess {
                _ui.update { it.copy(actionInProgress = false) }
                refresh()
            }.onFailure { e ->
                _ui.update { it.copy(actionInProgress = false, actionError = friendlyError(e)) }
            }
        }
    }

    fun consumeActionError() {
        _ui.update { it.copy(actionError = null) }
    }

    fun deleteFamily() {
        val family = _ui.value.family ?: return
        if (_ui.value.deleting) return
        _ui.update { it.copy(deleting = true, actionError = null) }
        viewModelScope.launch {
            runCatching { repo.deleteFamily(family.id) }
                .onSuccess { _ui.update { it.copy(deleting = false, deleted = true) } }
                .onFailure { e ->
                    _ui.update { it.copy(deleting = false, actionError = friendlyError(e)) }
                }
        }
    }
}
