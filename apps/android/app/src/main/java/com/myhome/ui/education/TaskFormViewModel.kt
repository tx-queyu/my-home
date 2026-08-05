package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.CourseDto
import com.myhome.net.dto.MemberInfo
import com.myhome.net.dto.TaskDto
import com.myhome.net.dto.TaskRequest
import com.myhome.repo.CourseRepository
import com.myhome.repo.FamilyRepository
import com.myhome.repo.TaskRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskFormUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val courses: List<CourseDto> = emptyList(),
    val children: List<MemberInfo> = emptyList(),
    val task: TaskDto? = null,
    val error: String? = null,
)

@HiltViewModel
class TaskFormViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val courseRepo: CourseRepository,
    private val familyRepo: FamilyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TaskFormUiState())
    val ui: StateFlow<TaskFormUiState> = _ui.asStateFlow()

    fun init(id: String?) {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { courseRepo.list() to familyRepo.listMembers() }
                .onSuccess { (courses, members) ->
                    val children = members.filter { it.isActive && "child" in it.roles }
                    if (id != null) {
                        runCatching { taskRepo.get(id) }
                            .onSuccess { task ->
                                _ui.update {
                                    it.copy(loading = false, courses = courses, children = children, task = task)
                                }
                            }
                            .onFailure { e ->
                                _ui.update {
                                    it.copy(loading = false, courses = courses, children = children, error = friendlyError(e))
                                }
                            }
                    } else {
                        _ui.update { it.copy(loading = false, courses = courses, children = children) }
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun save(
        id: String?,
        title: String,
        description: String?,
        courseId: String?,
        points: Int,
        dueDate: String?,
        isActive: Boolean,
        assigneeUserId: String?,
        availableStartDate: String?,
        availableEndDate: String?,
        availableStartTime: String?,
        availableEndTime: String?,
        recurrenceType: String,
        recurrenceWeekdays: List<Int>?,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val req = TaskRequest(
                title = title,
                description = description,
                courseId = courseId,
                points = points,
                dueDate = dueDate,
                isActive = isActive,
                assigneeUserId = assigneeUserId,
                availableStartDate = availableStartDate,
                availableEndDate = availableEndDate,
                availableStartTime = availableStartTime,
                availableEndTime = availableEndTime,
                recurrenceType = recurrenceType,
                recurrenceWeekdays = if (recurrenceType == "weekly") recurrenceWeekdays else null,
            )
            runCatching {
                if (id == null) taskRepo.create(req) else taskRepo.update(id, req)
            }
                .onSuccess { onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }
}
