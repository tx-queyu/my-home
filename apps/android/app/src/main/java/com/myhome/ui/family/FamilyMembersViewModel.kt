package com.myhome.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.MemberInfo
import com.myhome.repo.AuthRepository
import com.myhome.repo.FamilyRepository
import com.myhome.util.RoleUtil
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyMembersUiState(
    val loading: Boolean = true,
    val familyName: String = "",
    val members: List<MemberInfo> = emptyList(),
    val currentUserId: String? = null,
    val canManage: Boolean = false,
    val isFamilyAdmin: Boolean = false,
    val resetting: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class FamilyMembersViewModel @Inject constructor(
    private val familyRepo: FamilyRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FamilyMembersUiState())
    val ui: StateFlow<FamilyMembersUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val me = authRepo.me()
                val family = runCatching { familyRepo.getMyFamily() }.getOrNull()
                val members = familyRepo.listMembers()
                _ui.update {
                    it.copy(
                        loading = false,
                        familyName = family?.name.orEmpty(),
                        members = members,
                        currentUserId = me.id,
                        canManage = RoleUtil.canManageFamily(me.roles),
                        isFamilyAdmin = RoleUtil.isFamilyAdmin(me.roles),
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun deleteMember(id: String) {
        viewModelScope.launch {
            runCatching { familyRepo.deleteMember(id) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }

    fun toggleRole(member: MemberInfo, role: String) {
        if (member.id == _ui.value.currentUserId) return
        val newRoles = if (role in member.roles) {
            member.roles - role
        } else {
            member.roles + role
        }
        if (newRoles.isEmpty()) return  // 至少保留 1 个角色
        viewModelScope.launch {
            runCatching { familyRepo.updateMemberRoles(member.id, newRoles) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }

    fun resetPassword(member: MemberInfo, newPassword: String) {
        if (!_ui.value.canManage) return
        _ui.update { it.copy(resetting = true, error = null) }
        viewModelScope.launch {
            runCatching { familyRepo.resetMemberPassword(member.id, newPassword) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            resetting = false,
                            toast = "已重置 ${member.displayName} 的密码",
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
