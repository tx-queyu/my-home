package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.SystemFamilyDto
import com.myhome.repo.SystemRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyListUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val families: List<SystemFamilyDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 20,
    val error: String? = null,
    val query: String = "",
    // null=全部, true=仅有成员, false=仅空家庭
    val hasMembers: Boolean? = null,
) {
    val hasMore: Boolean get() = families.size < total
}

@HiltViewModel
class FamilyListViewModel @Inject constructor(
    private val repo: SystemRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FamilyListUiState())
    val ui: StateFlow<FamilyListUiState> = _ui.asStateFlow()

    fun refresh() {
        val s = _ui.value
        _ui.update { it.copy(loading = true, error = null, families = emptyList(), page = 0) }
        viewModelScope.launch {
            runCatching {
                repo.listFamiliesPage(
                    page = 1,
                    size = s.size,
                    q = s.query.trim().ifBlank { null },
                    hasMembers = s.hasMembers,
                )
            }.onSuccess { pg ->
                _ui.update {
                    it.copy(
                        loading = false,
                        families = pg.items,
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
                repo.listFamiliesPage(
                    page = nextPage,
                    size = s.size,
                    q = s.query.trim().ifBlank { null },
                    hasMembers = s.hasMembers,
                )
            }.onSuccess { pg ->
                _ui.update {
                    it.copy(
                        loadingMore = false,
                        families = it.families + pg.items,
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

    fun onHasMembersChange(v: Boolean?) {
        _ui.update { it.copy(hasMembers = v) }
        refresh()
    }
}
