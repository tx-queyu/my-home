package com.myhome.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.repo.AuthRepository
import com.myhome.repo.FamilyRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MineUiState(
    val loading: Boolean = true,
    val username: String = "",
    val displayName: String = "",
    val roles: List<String> = emptyList(),
    val familyName: String = "",
    val phone: String? = null,
    val phoneVerified: Boolean = false,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MineViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val familyRepo: FamilyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MineUiState())
    val ui: StateFlow<MineUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val me = authRepo.me()
                val family = runCatching { familyRepo.getMyFamily() }.getOrNull()
                _ui.update {
                    it.copy(
                        loading = false,
                        username = me.username,
                        displayName = me.displayName,
                        roles = me.roles,
                        familyName = family?.name.orEmpty(),
                        phone = me.phone,
                        phoneVerified = me.phoneVerified,
                        email = me.email,
                        emailVerified = me.emailVerified,
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}
