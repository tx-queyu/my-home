package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.HabitDto
import com.myhome.net.dto.SkillOverviewDto
import com.myhome.net.dto.TaskDto
import com.myhome.repo.HabitRepository
import com.myhome.repo.PointRepository
import com.myhome.repo.SkillRepository
import com.myhome.repo.TaskRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildEducationUiState(
    val loading: Boolean = false,
    val tasks: List<TaskDto> = emptyList(),
    val balance: Int = 0,
    val completedTaskIds: Set<String> = emptySet(),
    val mySkill: SkillOverviewDto? = null,
    // v0.17.0:打卡进度（拉取失败降级为空列表，卡片显示兜底文案）
    val habits: List<HabitDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ChildEducationViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val pointRepo: PointRepository,
    private val skillRepo: SkillRepository,
    private val habitRepo: HabitRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChildEducationUiState(loading = true))
    val ui: StateFlow<ChildEducationUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val tasksDeferred = async { runCatching { taskRepo.list() } }
            val skillDeferred = async { runCatching { skillRepo.myOverview() } }
            val recordsDeferred = async { runCatching { taskRepo.listRecords() } }
            val balanceDeferred = async { runCatching { pointRepo.me().balance } }
            val habitsDeferred = async { runCatching { habitRepo.list() } }

            val tasksR = tasksDeferred.await()
            val skill = skillDeferred.await().getOrNull()
            val records = recordsDeferred.await().getOrNull().orEmpty()
            val balance = balanceDeferred.await().getOrNull() ?: 0
            val habits = habitsDeferred.await().getOrNull().orEmpty()

            tasksR
                .onSuccess { tasks ->
                    val completedIds = records.map { it.taskId }.toSet()
                    _ui.update {
                        it.copy(
                            loading = false,
                            tasks = tasks,
                            balance = balance,
                            completedTaskIds = completedIds,
                            mySkill = skill,
                            habits = habits,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }
}
