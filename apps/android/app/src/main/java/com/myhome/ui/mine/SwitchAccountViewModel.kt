package com.myhome.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.UserInfo
import com.myhome.repo.AuthRepository
import com.myhome.storage.AccountStore
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SwitchAccountUiState(
    val loading: Boolean = true,
    val currentUser: UserInfo? = null,
    val accounts: List<AccountStore.SavedAccount> = emptyList(),
    val switching: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SwitchAccountViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SwitchAccountUiState())
    val ui: StateFlow<SwitchAccountUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.accountsFlow.collect { accounts ->
                _ui.update { it.copy(accounts = accounts, loading = false) }
            }
        }
        viewModelScope.launch {
            runCatching { authRepo.ensureCurrentAccountSaved() }
                .onSuccess { me -> _ui.update { it.copy(currentUser = me) } }
                .onFailure { /* 静默：未登录或网络失败时不高亮当前账号 */ }
        }
    }

    fun switchAccount(userId: String, onDone: () -> Unit) {
        if (_ui.value.switching) return
        _ui.update { it.copy(switching = true, error = null) }
        viewModelScope.launch {
            runCatching { authRepo.switchToAccount(userId) }
                .onSuccess { me ->
                    _ui.update { it.copy(switching = false, currentUser = me) }
                    if (me != null) onDone() else _ui.update { it.copy(error = "切换失败，账号信息缺失") }
                }
                .onFailure { e ->
                    _ui.update { it.copy(switching = false, error = friendlyError(e)) }
                }
        }
    }

    fun forgetAccount(userId: String) {
        viewModelScope.launch {
            authRepo.forgetAccount(userId)
        }
    }

    fun addNewAccount(onDone: () -> Unit) {
        if (_ui.value.switching) return
        _ui.update { it.copy(switching = true) }
        viewModelScope.launch {
            runCatching { authRepo.logout() }
                .onSuccess { _ui.update { it.copy(switching = false) }; onDone() }
                .onFailure { e ->
                    _ui.update { it.copy(switching = false, error = friendlyError(e)) }
                }
        }
    }
}
