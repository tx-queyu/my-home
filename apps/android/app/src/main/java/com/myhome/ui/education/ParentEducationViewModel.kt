package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.FamilyPointAccountDto
import com.myhome.net.dto.SelfStudyTextbookDto
import com.myhome.net.dto.SkillOverviewDto
import com.myhome.net.dto.TaskDto
import com.myhome.net.dto.TaskRecordDto
import com.myhome.net.dto.TextbookCoverageDto
import com.myhome.net.dto.TextbookOptionDto
import com.myhome.repo.PointRepository
import com.myhome.repo.RewardRepository
import com.myhome.repo.SelfStudyRepository
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
import java.time.LocalDate
import javax.inject.Inject

data class ChildSkillSummary(
    val child: FamilyPointAccountDto,
    val overview: SkillOverviewDto?,
    val textbooks: List<TextbookCoverageDto> = emptyList(),
)

data class ParentEducationUiState(
    val loading: Boolean = true,
    val accounts: List<FamilyPointAccountDto> = emptyList(),
    val childSkills: List<ChildSkillSummary> = emptyList(),
    val pendingRedemptionCount: Int = 0,
    val todayRecords: List<TaskRecordDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
    // v0.16.1 家长自学 tab:我的教材(教材 → 朗读/学习/测评课程)
    val selfTextbooks: List<SelfStudyTextbookDto> = emptyList(),
    // 添加教材弹窗的可选项(null=未加载)
    val availableTextbooks: List<TextbookOptionDto>? = null,
    val toast: String? = null,
    val error: String? = null,
) {
    val childAccounts: List<FamilyPointAccountDto>
        get() = accounts.filter { it.roles.contains("child") }

    val completerNameByTaskId: Map<String, String>
        get() = todayRecords.associate { rec ->
            rec.taskId to (accounts.firstOrNull { it.userId == rec.userId }?.displayName ?: "孩子")
        }

    val todayCompletedTaskIds: Set<String>
        get() = todayRecords.map { it.taskId }.toSet()
}

@HiltViewModel
class ParentEducationViewModel @Inject constructor(
    private val pointRepo: PointRepository,
    private val taskRepo: TaskRepository,
    private val rewardRepo: RewardRepository,
    private val skillRepo: SkillRepository,
    private val selfStudyRepo: SelfStudyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ParentEducationUiState())
    val ui: StateFlow<ParentEducationUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val accountsDeferred = async { runCatching { pointRepo.listFamilyAccounts() } }
            val recordsDeferred = async { runCatching { taskRepo.listRecords() } }
            val tasksDeferred = async { runCatching { taskRepo.list() } }
            val pendingDeferred = async { runCatching { rewardRepo.listRedemptions(status = "pending") } }
            // 自学教材:失败不阻塞主内容,仅自学 tab 显示空
            val selfTextbooksDeferred = async { runCatching { selfStudyRepo.listMyTextbooks() } }

            val accountsR = accountsDeferred.await()
            val recordsR = recordsDeferred.await()
            val tasksR = tasksDeferred.await()
            val pendingR = pendingDeferred.await()
            val selfTextbooks = selfTextbooksDeferred.await().getOrDefault(emptyList())

            val accounts = accountsR.getOrDefault(emptyList())
            val allRecords = recordsR.getOrDefault(emptyList())
            val tasks = tasksR.getOrDefault(emptyList())
            val pending = pendingR.getOrDefault(emptyList())

            // 能力概览 + 教材覆盖:对每个孩子并行拉 2 个端点,单点失败不阻塞整体
            val children = accounts.filter { it.roles.contains("child") }
            val skillDeferreds = children.map { child ->
                async {
                    val overviewR = async {
                        runCatching { skillRepo.childOverview(child.userId) }.getOrNull()
                    }
                    val textbooksR = async {
                        runCatching { skillRepo.childTextbooks(child.userId) }.getOrDefault(emptyList())
                    }
                    ChildSkillSummary(
                        child = child,
                        overview = overviewR.await(),
                        textbooks = textbooksR.await(),
                    )
                }
            }
            val childSkills = skillDeferreds.map { it.await() }

            val firstError = listOf(
                accountsR.exceptionOrNull(),
                recordsR.exceptionOrNull(),
                tasksR.exceptionOrNull(),
                pendingR.exceptionOrNull(),
            ).firstOrNull()

            if (firstError != null && accounts.isEmpty() && tasks.isEmpty()) {
                _ui.update {
                    it.copy(loading = false, error = friendlyError(firstError))
                }
                return@launch
            }

            val today = LocalDate.now().toString()
            _ui.update {
                it.copy(
                    loading = false,
                    accounts = accounts,
                    childSkills = childSkills,
                    todayRecords = allRecords.filter { r -> r.completedDate == today },
                    tasks = tasks,
                    pendingRedemptionCount = pending.size,
                    selfTextbooks = selfTextbooks,
                )
            }
        }
    }

    /** 打开「添加教材」弹窗时加载可选项（过滤:有互动课程且未添加过）。 */
    fun loadAvailableTextbooks() {
        if (_ui.value.availableTextbooks != null) return
        viewModelScope.launch {
            runCatching { selfStudyRepo.listAvailableTextbooks() }
                .onSuccess { options ->
                    _ui.update { it.copy(availableTextbooks = options) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(toast = friendlyError(e)) }
                }
        }
    }

    /** 添加教材到我的自学清单。 */
    fun addTextbook(subject: String, textbook: String) {
        viewModelScope.launch {
            runCatching { selfStudyRepo.addTextbook(subject, textbook) }
                .onSuccess { added ->
                    _ui.update {
                        it.copy(
                            selfTextbooks = it.selfTextbooks + added,
                            toast = "已添加 ${added.textbook}",
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(toast = friendlyError(e)) }
                }
        }
    }

    fun clearToast() {
        _ui.update { it.copy(toast = null) }
    }
}
