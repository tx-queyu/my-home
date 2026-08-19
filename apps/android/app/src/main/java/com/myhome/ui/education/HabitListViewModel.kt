package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.HabitDto
import com.myhome.net.dto.HabitLogDto
import com.myhome.repo.HabitRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitListUiState(
    val loading: Boolean = false,
    val checkingIn: String? = null, // 正在打卡的 habit id
    val habits: List<HabitDto> = emptyList(),
    val recentLogs: List<HabitLogDto> = emptyList(),
    val error: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepo: HabitRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HabitListUiState(loading = true))
    val ui: StateFlow<HabitListUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val habitsResult = runCatching { habitRepo.list() }
            // 最近打卡记录失败不阻塞主列表（家长视角才有日志段）
            val logs = runCatching { habitRepo.logs() }.getOrNull().orEmpty().take(20)
            habitsResult
                .onSuccess { habits ->
                    _ui.update { it.copy(loading = false, habits = habits, recentLogs = logs) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    /** 打卡：成功 toast 带积分；409 已打过也提示。 */
    fun checkIn(habit: HabitDto) {
        if (_ui.value.checkingIn != null) return
        _ui.update { it.copy(checkingIn = habit.id) }
        viewModelScope.launch {
            runCatching { habitRepo.checkIn(habit.id) }
                .onSuccess { log ->
                    _ui.update {
                        it.copy(
                            checkingIn = null,
                            toast = "打卡成功 +${log.pointsEarned} 积分",
                        )
                    }
                    refresh()
                }
                .onFailure { e ->
                    _ui.update { it.copy(checkingIn = null, toast = friendlyError(e)) }
                    // 已打过/已停用时刷新让 ✓ 状态及时显示
                    refresh()
                }
        }
    }

    fun delete(habitId: String) {
        viewModelScope.launch {
            runCatching { habitRepo.delete(habitId) }
                .onSuccess {
                    _ui.update { it.copy(toast = "已删除") }
                    refresh()
                }
                .onFailure { e -> _ui.update { it.copy(toast = friendlyError(e)) } }
        }
    }
}
