package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.MemberInfo
import com.myhome.net.dto.StudySessionDto
import com.myhome.net.dto.StudyStatsDto
import com.myhome.repo.FamilyRepository
import com.myhome.repo.StudySessionRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyStatsUiState(
    val loading: Boolean = false,
    val isParent: Boolean = false,
    val members: List<MemberInfo> = emptyList(),
    // null = 自己（家长视角：「我自己」+ 各孩子）
    val selectedUserId: String? = null,
    val stats: StudyStatsDto? = null,
    val recent: List<StudySessionDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class StudyStatsViewModel @Inject constructor(
    private val studyRepo: StudySessionRepository,
    private val familyRepo: FamilyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(StudyStatsUiState(loading = true))
    val ui: StateFlow<StudyStatsUiState> = _ui.asStateFlow()

    fun init(isParent: Boolean) {
        _ui.update { it.copy(loading = true, error = null, isParent = isParent) }
        viewModelScope.launch {
            val members = if (isParent) {
                runCatching { familyRepo.listMembers() }.getOrNull().orEmpty()
                    .filter { it.isActive && "child" in it.roles }
            } else emptyList()
            _ui.update { it.copy(members = members, loading = false) }
            load(isParent, null) // 默认看自己
        }
    }

    fun selectUser(userId: String?) {
        _ui.update { it.copy(selectedUserId = userId, loading = true) }
        load(_ui.value.isParent, userId)
    }

    private fun load(isParent: Boolean, userId: String?) {
        viewModelScope.launch {
            val statsDeferred = async { runCatching { studyRepo.stats(userId) } }
            val recentDeferred = async { runCatching { studyRepo.list(userId) } }
            val statsResult = statsDeferred.await()
            val recent = recentDeferred.await().getOrNull().orEmpty()
            statsResult
                .onSuccess { stats ->
                    _ui.update { s -> s.copy(loading = false, stats = stats, recent = recent) }
                }
                .onFailure { e ->
                    _ui.update { s -> s.copy(loading = false, error = friendlyError(e)) }
                }
        }
    }
}
