package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.CourseDto
import com.myhome.net.dto.CourseExperienceResult
import com.myhome.repo.CourseRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseListUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val courses: List<CourseDto> = emptyList(),
    val error: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class CourseListViewModel @Inject constructor(
    private val repo: CourseRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CourseListUiState())
    val ui: StateFlow<CourseListUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.adminList() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, courses = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun activate(id: String) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.activate(id) }
                .onSuccess { refreshAfterMutation("已激活") }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun deactivate(id: String) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.deactivate(id) }
                .onSuccess { refreshAfterMutation("已停用") }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun experience(
        course: CourseDto,
        onResult: (CourseExperienceResult) -> Unit = {},
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.experience(course.id) }
                .onSuccess { r ->
                    _ui.update {
                        it.copy(
                            saving = false,
                            toast = "已为 ${r.childUsername} 完成「${r.taskTitle}」, +${r.pointsEarned} 积分",
                        )
                    }
                    onResult(r)
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    private fun refreshAfterMutation(toast: String) {
        _ui.update { it.copy(saving = false, toast = toast) }
        refresh()
    }
}
