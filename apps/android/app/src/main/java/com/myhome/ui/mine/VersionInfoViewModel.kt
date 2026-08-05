package com.myhome.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.VersionInfoDto
import com.myhome.repo.VersionRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VersionInfoUiState(
    val loading: Boolean = true,
    val info: VersionInfoDto? = null,
    val error: String? = null,
)

@HiltViewModel
class VersionInfoViewModel @Inject constructor(
    private val versionRepo: VersionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(VersionInfoUiState())
    val ui: StateFlow<VersionInfoUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { versionRepo.fetchVersionInfo() }
                .onSuccess { info -> _ui.update { it.copy(loading = false, info = info) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }
}
