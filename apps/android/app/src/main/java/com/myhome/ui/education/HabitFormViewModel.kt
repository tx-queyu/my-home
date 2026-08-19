package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.HabitCreateRequest
import com.myhome.net.dto.HabitDto
import com.myhome.net.dto.HabitUpdateRequest
import com.myhome.repo.HabitRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitFormUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val existing: HabitDto? = null,
    val error: String? = null,
)

@HiltViewModel
class HabitFormViewModel @Inject constructor(
    private val habitRepo: HabitRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HabitFormUiState())
    val ui: StateFlow<HabitFormUiState> = _ui.asStateFlow()

    fun init(habitId: String?) {
        if (habitId == null) {
            _ui.update { it.copy(loading = false, existing = null) }
            return
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { habitRepo.list(includeInactive = true).firstOrNull { it.id == habitId } }
                .onSuccess { habit ->
                    if (habit == null) {
                        _ui.update { it.copy(loading = false, error = "习惯不存在") }
                    } else {
                        _ui.update { it.copy(loading = false, existing = habit) }
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun save(
        id: String?,
        name: String,
        points: Int,
        streakCap: Int,
        isActive: Boolean,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (id == null) {
                    habitRepo.create(
                        HabitCreateRequest(
                            name = name,
                            points = points,
                            streakCap = streakCap,
                            isActive = isActive,
                        )
                    )
                } else {
                    habitRepo.update(
                        id,
                        HabitUpdateRequest(
                            name = name,
                            points = points,
                            streakCap = streakCap,
                            isActive = isActive,
                        ),
                    )
                }
            }
                .onSuccess {
                    _ui.update { it.copy(saving = false) }
                    onDone()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun delete(habitId: String, onDone: () -> Unit) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { habitRepo.delete(habitId) }
                .onSuccess { _ui.update { it.copy(saving = false) }; onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }
}
