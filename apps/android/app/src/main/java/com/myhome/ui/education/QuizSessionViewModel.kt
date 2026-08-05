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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 测评课 session（v0.15.0）：15 题全拼写（中→英），分层抽样覆盖各能力带。
 *
 *   LOADING → QUESTION(i) → FEEDBACK(i, 短暂自动跳) → QUESTION(i+1) … → REPORT → (finished)
 *
 * 判对：忽略大小写精确匹配，对=100 / 错=0，回写全局 lexeme mastery（与朗读/学习共享）。
 * 任务模式答完才可点「完成任务」；体验模式进 REPORT 自动结算积分。
 */
enum class QuizPhase { LOADING, QUESTION, FEEDBACK, REPORT }

data class QuizItemResult(
    val spelling: String,
    val meaningCn: String?,
    val correct: Boolean,
)

data class QuizSessionUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val courseLabel: String = "",
    val courseId: String = "",
    val words: List<WordDto> = emptyList(),
    val currentIndex: Int = 0,
    val phase: QuizPhase = QuizPhase.LOADING,
    val answerInput: String = "",
    val lastCorrect: Boolean = false,
    val submitting: Boolean = false,
    val results: List<QuizItemResult> = emptyList(),
    val finishing: Boolean = false,
    val finished: Boolean = false,
    val earnedPoints: Int = 0,
    val result: CourseExperienceResult? = null,
    val toast: String? = null,
    // 任务模式（taskId != null）：走 taskRepo.complete 而非 experience
    val taskId: String? = null,
    val taskPoints: Int? = null,
    // 家长自学(v0.16.0):不结算积分,进 REPORT 只展示报告
    val selfStudy: Boolean = false,
) {
    val currentWord: WordDto? get() = words.getOrNull(currentIndex)
    val totalWords: Int get() = words.size
    val correctCount: Int get() = results.count { it.correct }
}

@HiltViewModel
class QuizSessionViewModel @Inject constructor(
    private val repo: CourseRepository,
    private val taskRepo: TaskRepository,
    val ttsPlayer: ReadingTtsPlayer,
) : ViewModel() {

    private val _ui = MutableStateFlow(QuizSessionUiState())
    val ui: StateFlow<QuizSessionUiState> = _ui.asStateFlow()

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
                phase = QuizPhase.LOADING,
            )
        }
        viewModelScope.launch {
            runCatching {
                val course = repo.get(courseId)
                val words = repo.listNextWords(courseId, limit = QUESTIONS_PER_SESSION, mode = "assess")
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
                        phase = QuizPhase.QUESTION,
                        taskPoints = taskPoints,
                    )
                }
            }.onFailure { e ->
                loadedKey = null
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun onAnswerInputChange(v: String) {
        _ui.update { it.copy(answerInput = v) }
    }

    /** 答题提交：忽略大小写精确判对，对=100 / 错=0 回写 mastery。短暂反馈后自动下一题。 */
    fun submitAnswer() {
        val s = _ui.value
        if (s.phase != QuizPhase.QUESTION || s.submitting) return
        val word = s.currentWord ?: return
        if (s.answerInput.isBlank()) return
        val correct = s.answerInput.trim().equals(word.spelling, ignoreCase = true)
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
                    phase = QuizPhase.FEEDBACK,
                    lastCorrect = correct,
                    results = it.results + QuizItemResult(word.spelling, word.meaningCn, correct),
                )
            }
            delay(FEEDBACK_MS)
            advance()
        }
    }

    private fun advance() {
        val s = _ui.value
        if (s.phase != QuizPhase.FEEDBACK) return
        val next = s.currentIndex + 1
        if (next >= s.words.size) {
            _ui.update { it.copy(phase = QuizPhase.REPORT, answerInput = "") }
            if (s.taskId == null && !s.selfStudy) finishExperience()
        } else {
            _ui.update { it.copy(currentIndex = next, phase = QuizPhase.QUESTION, answerInput = "") }
        }
    }

    /** 任务模式：REPORT 手动完成任务（答完才可点），后端校验 + 自动加积分。 */
    fun finishTask() {
        val s = _ui.value
        val taskId = s.taskId ?: return
        if (s.finishing || s.finished) return
        if (s.phase != QuizPhase.REPORT) {
            _ui.update { it.copy(toast = "答完所有题目后才能完成") }
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

    /** 体验模式：进 REPORT 自动结算（仿朗读自然结束）。 */
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
        const val QUESTIONS_PER_SESSION = 15
        const val SCORE_CORRECT = 100
        const val SCORE_WRONG = 0
        const val FEEDBACK_MS = 1500L
    }
}
