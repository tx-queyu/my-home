package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.CourseExperienceResult
import com.myhome.net.dto.WordDto
import com.myhome.repo.CourseRepository
import com.myhome.repo.TaskRepository
import com.myhome.util.ReadingTtsPlayer
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 学习课 session（v0.15.0）：学习卡 + 拼写卡两步走，10 词一轮。
 *
 *   LOADING → STUDY(i) → SPELL(i) → FEEDBACK(i) → STUDY(i+1) … → SUMMARY → (finished)
 *
 * 判对：忽略大小写精确匹配，对=50 / 错=20，回写全局 lexeme mastery（与朗读/测评共享）。
 * 任务模式学完才可点「完成任务」；体验模式进 SUMMARY 自动结算积分（仿朗读自然结束）。
 */
enum class LearnPhase { LOADING, STUDY, SPELL, FEEDBACK, SUMMARY }

data class LearnSessionUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val courseLabel: String = "",
    val courseId: String = "",
    val words: List<WordDto> = emptyList(),
    val currentIndex: Int = 0,
    val phase: LearnPhase = LearnPhase.LOADING,
    val spellInput: String = "",
    val lastCorrect: Boolean = false,
    val submitting: Boolean = false,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val finishing: Boolean = false,
    val finished: Boolean = false,
    val earnedPoints: Int = 0,
    val result: CourseExperienceResult? = null,
    val toast: String? = null,
    // 任务模式（taskId != null）：走 taskRepo.complete 而非 experience
    val taskId: String? = null,
    val taskPoints: Int? = null,
    // 家长自学(v0.16.0):不结算积分,进 SUMMARY 只展示成果
    val selfStudy: Boolean = false,
) {
    val currentWord: WordDto? get() = words.getOrNull(currentIndex)
    val totalWords: Int get() = words.size
}

@HiltViewModel
class LearnSessionViewModel @Inject constructor(
    private val repo: CourseRepository,
    private val taskRepo: TaskRepository,
    val ttsPlayer: ReadingTtsPlayer,
) : ViewModel() {

    private val _ui = MutableStateFlow(LearnSessionUiState())
    val ui: StateFlow<LearnSessionUiState> = _ui.asStateFlow()

    private var loadedKey: String? = null

    fun load(courseId: String) = start(courseId, taskId = null)

    fun loadSelfStudy(courseId: String) = start(courseId, taskId = null, selfStudy = true)

    fun loadForTask(courseId: String, taskId: String) = start(courseId, taskId = taskId)

    private fun start(courseId: String, taskId: String?, selfStudy: Boolean = false) {
        val key = "$courseId:${taskId ?: ""}:$selfStudy"
        if (loadedKey == key) return
        loadedKey = key
        _ui.update {
            it.copy(
                loading = true, error = null, courseId = courseId, taskId = taskId,
                selfStudy = selfStudy,
                phase = LearnPhase.LOADING,
            )
        }
        viewModelScope.launch {
            runCatching {
                val course = repo.get(courseId)
                val words = repo.listNextWords(courseId, limit = WORDS_PER_SESSION, mode = "learn")
                val taskPoints = taskId?.let { id -> taskRepo.get(id).points }
                Triple(course, words, taskPoints)
            }.onSuccess { (course, words, taskPoints) ->
                if (words.isEmpty()) {
                    loadedKey = null
                    _ui.update {
                        it.copy(
                            loading = false,
                            courseLabel = "${course.textbook} · ${course.learningMethod}",
                            error = "该课程还没有单词",
                        )
                    }
                    return@onSuccess
                }
                _ui.update {
                    it.copy(
                        loading = false,
                        courseLabel = "${course.textbook} · ${course.learningMethod}",
                        words = words,
                        currentIndex = 0,
                        phase = LearnPhase.STUDY,
                        taskPoints = taskPoints,
                    )
                }
            }.onFailure { e ->
                loadedKey = null
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun onSpellInputChange(v: String) {
        _ui.update { it.copy(spellInput = v) }
    }

    /** 学习卡 → 拼写卡。 */
    fun startSpell() {
        val s = _ui.value
        if (s.phase != LearnPhase.STUDY) return
        ttsPlayer.stop()
        _ui.update { it.copy(phase = LearnPhase.SPELL, spellInput = "") }
    }

    /** 拼写提交：忽略大小写精确判对，对=50 / 错=20 回写 mastery。评分失败不阻塞流程，仅提示。 */
    fun submitSpelling() {
        val s = _ui.value
        if (s.phase != LearnPhase.SPELL || s.submitting) return
        val word = s.currentWord ?: return
        if (s.spellInput.isBlank()) return
        val correct = s.spellInput.trim().equals(word.spelling, ignoreCase = true)
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            runCatching {
                repo.submitWordScore(s.courseId, word.id, if (correct) SCORE_CORRECT else SCORE_WRONG)
            }.onFailure { e ->
                _ui.update { it.copy(toast = friendlyError(e)) }
            }
            _ui.update {
                it.copy(
                    submitting = false,
                    phase = LearnPhase.FEEDBACK,
                    lastCorrect = correct,
                    correctCount = it.correctCount + if (correct) 1 else 0,
                    wrongCount = it.wrongCount + if (correct) 0 else 1,
                )
            }
        }
    }

    /** 反馈卡 → 下一词学习卡；最后一词 → SUMMARY（体验模式自动结算积分；自学不结算）。 */
    fun nextWord() {
        val s = _ui.value
        if (s.phase != LearnPhase.FEEDBACK) return
        val next = s.currentIndex + 1
        if (next >= s.words.size) {
            _ui.update { it.copy(phase = LearnPhase.SUMMARY) }
            if (s.taskId == null && !s.selfStudy) finishExperience()
        } else {
            _ui.update { it.copy(currentIndex = next, phase = LearnPhase.STUDY, spellInput = "") }
        }
    }

    /** 任务模式：SUMMARY 手动完成任务（学完才可点），后端校验 + 自动加积分。 */
    fun finishTask() {
        val s = _ui.value
        val taskId = s.taskId ?: return
        if (s.finishing || s.finished) return
        if (s.phase != LearnPhase.SUMMARY) {
            _ui.update { it.copy(toast = "学完所有单词后才能完成") }
            return
        }
        _ui.update { it.copy(finishing = true) }
        ttsPlayer.stop()
        viewModelScope.launch {
            runCatching { taskRepo.complete(taskId) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            finishing = false,
                            finished = true,
                            earnedPoints = s.taskPoints ?: 0,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(finishing = false, finished = true, error = friendlyError(e))
                    }
                }
        }
    }

    /** 体验模式：进 SUMMARY 自动结算（仿朗读自然结束）。 */
    private fun finishExperience() {
        val s = _ui.value
        if (s.finishing || s.finished) return
        _ui.update { it.copy(finishing = true) }
        viewModelScope.launch {
            runCatching { repo.experience(s.courseId) }
                .onSuccess { r ->
                    _ui.update {
                        it.copy(
                            finishing = false,
                            finished = true,
                            result = r,
                            earnedPoints = r.pointsEarned,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(finishing = false, finished = true, error = friendlyError(e))
                    }
                }
        }
    }

    fun clearToast() {
        _ui.update { it.copy(toast = null) }
    }

    override fun onCleared() {
        ttsPlayer.release()
        super.onCleared()
    }

    companion object {
        const val WORDS_PER_SESSION = 10
        const val SCORE_CORRECT = 50
        const val SCORE_WRONG = 20
    }
}
