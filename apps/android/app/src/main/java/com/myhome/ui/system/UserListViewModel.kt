package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.SystemFamilyDto
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

data class UserListUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val users: List<SystemUserDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 20,
    val error: String? = null,
    val query: String = "",
    val familyId: String? = null,  // null=全部, "none"=无家庭, UUID=指定家庭
    val role: String? = null,
    val active: Boolean? = null,
    val families: List<SystemFamilyDto> = emptyList(),
) {
    val hasMore: Boolean get() = users.size < total
}

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val repo: SystemRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(UserListUiState())
    val ui: StateFlow<UserListUiState> = _ui.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            runCatching { repo.listFamilies() }
                .onSuccess { fams -> _ui.update { it.copy(families = fams) } }
        }
    }

    fun refresh() {
        val s = _ui.value
        _ui.update { it.copy(loading = true, error = null, users = emptyList(), page = 0) }
        viewModelScope.launch {
            runCatching {
                repo.listUsersPage(
                    page = 1,
                    size = s.size,
                    familyId = s.familyId,
                    role = s.role,
                    active = s.active,
                    q = s.query.trim().ifBlank { null },
                )
            }.onSuccess { pg ->
                _ui.update {
                    it.copy(
                        loading = false,
                        users = pg.items,
                        total = pg.total,
                        page = 1,
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val nextPage = s.page + 1
            runCatching {
                repo.listUsersPage(
                    page = nextPage,
                    size = s.size,
                    familyId = s.familyId,
                    role = s.role,
                    active = s.active,
                    q = s.query.trim().ifBlank { null },
                )
            }.onSuccess { pg ->
                _ui.update {
                    it.copy(
                        loadingMore = false,
                        users = it.users + pg.items,
                        page = nextPage,
                        total = pg.total,
                    )
                }
            }.onFailure {
                _ui.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun onQueryChange(v: String) {
        _ui.update { it.copy(query = v) }
    }

    fun onSearchSubmit() = refresh()

    fun onFamilyChange(familyId: String?) {
        _ui.update { it.copy(familyId = familyId) }
        refresh()
    }

    fun onRoleChange(role: String?) {
        _ui.update { it.copy(role = role) }
        refresh()
    }

    fun onActiveChange(active: Boolean?) {
        _ui.update { it.copy(active = active) }
        refresh()
    }
}
