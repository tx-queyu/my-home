package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.CourseSessionType
import com.myhome.net.dto.TaskDto
import com.myhome.net.dto.sessionType
import com.myhome.repo.AuthRepository
import com.myhome.repo.CourseRepository
import com.myhome.repo.TaskRepository
import com.myhome.util.RoleUtil
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskDetailUiState(
    val loading: Boolean = false,
    val task: TaskDto? = null,
    val isParent: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null,
    // v0.15.0：英语互动课程的 session 形态（朗读/学习/测评）；null=非互动课程
    val sessionType: CourseSessionType? = null,
    // null=未识别为互动课程任务或未拉取, true=有单词走互动流程, false=互动课程但单词为空 fallback 到标记完成
    val hasWords: Boolean? = null,
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repo: TaskRepository,
    private val authRepo: AuthRepository,
    private val courseRepo: CourseRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TaskDetailUiState(loading = true))
    val ui: StateFlow<TaskDetailUiState> = _ui.asStateFlow()

    fun load(id: String) {
        _ui.update { it.copy(loading = true, error = null, sessionType = null, hasWords = null) }
        viewModelScope.launch {
            val roles = runCatching { authRepo.me().roles }.getOrNull().orEmpty()
            val isParent = RoleUtil.canManageFamily(roles)
            runCatching { repo.get(id) }
                .onSuccess { task ->
                    val completed = if (task.recurrenceType == "one_off") {
                        val records = runCatching { repo.listRecords() }.getOrNull().orEmpty()
                        records.any { it.taskId == id }
                    } else {
                        task.completedToday
                    }
                    // 互动课程任务（朗读/学习/测评）拉词表判断是否启用「开始」入口（仅 KET 等已种词教材生效）
                    val sessionType = task.course?.sessionType()
                    val hasWords = if (sessionType != null && task.courseId != null) {
                        runCatching { courseRepo.listWords(task.courseId).isNotEmpty() }.getOrNull()
                    } else null
                    _ui.update {
                        it.copy(
                            loading = false,
                            task = task,
                            isParent = isParent,
                            completed = completed,
                            sessionType = sessionType,
                            hasWords = hasWords,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun complete(id: String) {
        viewModelScope.launch {
            runCatching { repo.complete(id) }
                .onSuccess {
                    _ui.update { it.copy(completed = true) }
                }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onSuccess { onDone() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyError(e)) } }
        }
    }
}
