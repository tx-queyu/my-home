package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.ChildWordMasteryDto
import com.myhome.net.dto.SkillOverviewDto
import com.myhome.net.dto.TextbookCoverageDto
import com.myhome.repo.SkillRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SkillCenterMode {
    data object Self : SkillCenterMode
    data class Child(val childId: String, val childName: String) : SkillCenterMode
}

data class SkillCenterUiState(
    val loading: Boolean = true,
    val overview: SkillOverviewDto? = null,
    val textbooks: List<TextbookCoverageDto> = emptyList(),
    val selectedTextbookKey: String? = null,
    val words: List<ChildWordMasteryDto> = emptyList(),
    val wordsLoading: Boolean = false,
    val wordsError: String? = null,
    val selectedFilter: String? = null,
    val error: String? = null,
) {
    val selectedTextbook: TextbookCoverageDto?
        get() = textbooks.firstOrNull { it.key == selectedTextbookKey }

    val filteredWords: List<ChildWordMasteryDto>
        get() = if (selectedFilter == null) words
        else words.filter { it.state == selectedFilter }
}

@HiltViewModel
class SkillCenterViewModel @Inject constructor(
    private val skillRepo: SkillRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SkillCenterUiState())
    val ui: StateFlow<SkillCenterUiState> = _ui.asStateFlow()

    private var mode: SkillCenterMode = SkillCenterMode.Self

    fun load(mode: SkillCenterMode) {
        this.mode = mode
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val overviewDeferred = async {
                runCatching {
                    when (mode) {
                        is SkillCenterMode.Self -> skillRepo.myOverview()
                        is SkillCenterMode.Child -> skillRepo.childOverview(mode.childId)
                    }
                }
            }
            val textbooksDeferred = async {
                runCatching {
                    when (mode) {
                        is SkillCenterMode.Self -> skillRepo.myTextbooks()
                        is SkillCenterMode.Child -> skillRepo.childTextbooks(mode.childId)
                    }
                }
            }

            val overviewR = overviewDeferred.await()
            val textbooksR = textbooksDeferred.await()

            val overview = overviewR.getOrNull()
            val textbooks = textbooksR.getOrDefault(emptyList())
            val err = overviewR.exceptionOrNull() ?: textbooksR.exceptionOrNull()

            if (overview == null && err != null) {
                _ui.update { it.copy(loading = false, error = friendlyError(err)) }
                return@launch
            }
            val selected = textbooks.firstOrNull()?.key
            _ui.update {
                it.copy(
                    loading = false,
                    overview = overview,
                    textbooks = textbooks,
                    selectedTextbookKey = selected,
                    selectedFilter = null,
                )
            }
            if (selected != null) {
                fetchWords(selected)
            } else {
                _ui.update { it.copy(words = emptyList(), wordsLoading = false, wordsError = null) }
            }
        }
    }

    fun selectTextbook(key: String) {
        if (key == _ui.value.selectedTextbookKey) return
        _ui.update { it.copy(selectedTextbookKey = key, selectedFilter = null) }
        fetchWords(key)
    }

    fun retryWords() {
        _ui.value.selectedTextbookKey?.let { fetchWords(it) }
    }

    private fun fetchWords(textbookKey: String) {
        val subject = textbookKey.substringBefore('|')
        val textbook = textbookKey.substringAfter('|')
        _ui.update { it.copy(wordsLoading = true, wordsError = null) }
        viewModelScope.launch {
            runCatching {
                when (val m = mode) {
                    is SkillCenterMode.Self -> skillRepo.myWords(subject = subject, textbook = textbook)
                    is SkillCenterMode.Child -> skillRepo.childWords(
                        m.childId, subject = subject, textbook = textbook,
                    )
                }
            }.onSuccess { words ->
                _ui.update { it.copy(wordsLoading = false, words = words) }
            }.onFailure { e ->
                _ui.update {
                    it.copy(wordsLoading = false, words = emptyList(), wordsError = friendlyError(e))
                }
            }
        }
    }

    fun setFilter(state: String?) {
        _ui.update { it.copy(selectedFilter = state) }
    }
}
