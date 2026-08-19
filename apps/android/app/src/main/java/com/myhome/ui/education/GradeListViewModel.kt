package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.GradeDto
import com.myhome.net.dto.MemberInfo
import com.myhome.repo.FamilyRepository
import com.myhome.repo.GradeRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradeListUiState(
    val loading: Boolean = false,
    val isParent: Boolean = false,
    val grades: List<GradeDto> = emptyList(),
    val members: List<MemberInfo> = emptyList(),
    // null = 全部（家长）
    val selectedUserId: String? = null,
    val error: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class GradeListViewModel @Inject constructor(
    private val gradeRepo: GradeRepository,
    private val familyRepo: FamilyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(GradeListUiState(loading = true))
    val ui: StateFlow<GradeListUiState> = _ui.asStateFlow()

    fun refresh(isParent: Boolean) {
        _ui.update { it.copy(loading = true, error = null, isParent = isParent) }
        viewModelScope.launch {
            val gradesDeferred = async { runCatching { gradeRepo.list() } }
            val members = if (isParent) {
                runCatching { familyRepo.listMembers() }.getOrNull().orEmpty()
            } else emptyList()
            gradesDeferred.await()
                .onSuccess { grades ->
                    _ui.update { it.copy(loading = false, grades = grades, members = members) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun selectUser(userId: String?) {
        _ui.update { it.copy(selectedUserId = userId) }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    fun delete(gradeId: String) {
        viewModelScope.launch {
            runCatching { gradeRepo.delete(gradeId) }
                .onSuccess {
                    _ui.update { it.copy(toast = "已删除") }
                    refresh(_ui.value.isParent)
                }
                .onFailure { e -> _ui.update { it.copy(toast = friendlyError(e)) } }
        }
    }
}
