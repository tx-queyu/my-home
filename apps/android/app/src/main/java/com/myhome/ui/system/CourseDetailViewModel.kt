package com.myhome.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.CourseDto
import com.myhome.repo.CourseRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseDetailUiState(
    val loading: Boolean = true,
    val course: CourseDto? = null,
    val error: String? = null,
)

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    private val repo: CourseRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CourseDetailUiState())
    val ui: StateFlow<CourseDetailUiState> = _ui.asStateFlow()

    fun load(courseId: String) {
        if (_ui.value.course?.id == courseId && !_ui.value.loading) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.get(courseId) }
                .onSuccess { c -> _ui.update { it.copy(loading = false, course = c) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun refresh() {
        val id = _ui.value.course?.id ?: return
        _ui.update { it.copy(course = null) }  // 强制重拉
        load(id)
    }
}
