package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.CourseExperienceResult
import com.myhome.net.dto.WordAssessmentResult
import com.myhome.net.dto.WordDto
import com.myhome.repo.CourseRepository
import com.myhome.repo.TaskRepository
import com.myhome.util.AudioRecorder
import com.myhome.util.ReadingTtsPlayer
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * 朗读练习 —— TTS 对话驱动状态机（v0.11.6：3 次跟读 + 拼+读合一 + 评分）
 *
 *   LOADING (拉词表)
 *     ↓
 *   INTRO_PHASE (TTS 开场白 "我们下面开始朗读练习，请跟我读")
 *     ↓ onTtsDone
 *   WORD_INTRO (TTS: "下一个单词是 apple，意思是苹果，音节 ap-ple")
 *     ↓ onTtsDone + 0.5s
 *   WORD_PRACTICE round=1 (TTS: "跟我读 apple"，用户跟读，不评分)
 *     ↓ onTtsDone + 1.5s
 *   WORD_PRACTICE round=2 (TTS: "再来一遍，apple")
 *     ↓ onTtsDone + 1.5s
 *   WORD_PRACTICE round=3 (TTS: "最后一次，apple")
 *     ↓ onTtsDone + 1.5s
 *   WORD_SPELL_READ (TTS: "我们拼一下 D O G dog" —— 字母拼读 + 单词连读合一)
 *     ↓ onTtsDone + 0.5s
 *   LISTENING (录音 3.5s，用户连起来 "D O G dog" 一次录完)
 *     ↓
 *   ASSESSING (上传 PCM → ISE 评分，ref_text 多行 paper "D\nO\nG\ndog")
 *     ↓
 *   FEEDBACK (TTS: 按分数反馈)
 *     ↓ onTtsDone
 *     ├─ score≥60 → currentIndex++ → WORD_INTRO
 *     ├─ score<60 + finalAttempts<3 → finalAttempts++ → WORD_SPELL_READ（重读）
 *     └─ score<60 + finalAttempts≥3 → currentIndex++ → WORD_INTRO（强制跳过）
 *
 *   终止路径：
 *     - 时间到 20:00 → FINALE (TTS 结束语) → /experience 加积分 → FINISHED
 *     - 用户提前结束 → 二次确认 → FINALE (不计积分结束语) → 不调 experience → FINISHED
 *     - 用户暂停 → PAUSED (timer 冻结) → 恢复后重新领读当前单词
 */
enum class ReadingPhase {
    LOADING,
    INTRO_PHASE,
    WORD_INTRO,
    WORD_PRACTICE,
    WORD_PRACTICE_LISTEN,  // v0.11.7：跟读阶段 TTS 完成后录 2s
    ASSESSING_PRACTICE,    // v0.11.8：跟读评分中（ISE read_word）
    WORD_SPELL_READ,
    LISTENING,
    ASSESSING,
    FEEDBACK,
    PAUSED,
    FINALE,
    FINISHED,
    FAILED,
}

/** 反馈级别：决定 TTS 反馈文案池。 */
enum class FeedbackLevels {
    NONE,
    EXCELLENT,  // score >= 90
    GOOD,       // 75..89
    PASS,       // 60..74
    RETRY,      // <60 且 finalAttempts < 3
    SKIP,       // <60 且 finalAttempts >= 3，强制跳过
    SILENT,     // 录音太短 / is_rejected
    ERROR,      // ISE 异常或提前结束
}

/** TTS 文案池 —— 同级别随机选一条，避免机械感。 */
internal object VoiceLines {
    const val INTRO = "我们下面开始朗读练习。我会带你读每个单词三遍，然后我们一起拼一次，最后你完整朗读一遍，让评分老师给你打分。准备好了吗？我们开始。"

    /** 单词讲解：拼写 + 中文 + 音节 + 例句（英 + 中） */
    fun wordIntro(
        spelling: String,
        meaningCn: String?,
        syllables: List<String>,
        sampleSentence: String?,
        sampleSentenceTranslation: String?,
    ): String {
        val sb = StringBuilder()
        sb.append("下一个单词是，").append(spelling).append("。")
        if (!meaningCn.isNullOrBlank()) {
            sb.append("意思是，").append(meaningCn).append("。")
        }
        if (syllables.isNotEmpty()) {
            sb.append("音节拆分，")
            sb.append(syllables.joinToString(" - "))
            sb.append("。")
        }
        if (!sampleSentence.isNullOrBlank()) {
            sb.append("例句，").append(sampleSentence).append("。")
            if (!sampleSentenceTranslation.isNullOrBlank()) {
                sb.append(sampleSentenceTranslation)
            }
        }
        return sb.toString()
    }

    /** 跟读领读：根据轮次不同前缀，避免机械。 */
    fun practice(spelling: String, round: Int): String {
        val prefix = when (round) {
            1 -> "跟我读，"
            2 -> "再来一遍，"
            3 -> "最后一次，"
            else -> ""
        }
        return prefix + spelling
    }

    /** 字母拼读+连读合一领读："我们拼一下 D, O, G. dog"。
     *  - 字母间用英文逗号 + 空格 "D, O, G"（中等停顿 ~200ms，比纯空格清晰，比中文逗号紧凑）
     *  - 字母组末尾用句号 "."（长停顿 ~400ms，明显分隔拼读和单词朗读）
     *  v0.11.8：字母间从空格改回英文逗号（用户反馈空格太短），句号保留分隔拼+读。 */
    fun spellAndRead(spelling: String): String {
        val letters = spelling.uppercase().toCharArray().joinToString(", ")
        return "我们拼一下，$letters. $spelling"
    }

    /** ISE 多行 paper 参考文本：D\nO\nG\ndog（每个字母一行 + 单词一行）。
     *  read_word + 多行 paper 已验证可正确评估整段录音（字母测连贯，单词测准确）。 */
    fun spellReadRefText(spelling: String): String {
        val letters = spelling.uppercase().toCharArray().joinToString("\n")
        return "$letters\n$spelling"
    }

    val EXCELLENT = listOf("太棒了！", "非常完美！", "真厉害！", "好极了！")
    val GOOD = listOf("不错哦！", "读得很好！", "正确的！", "好样的！")
    val PASS = listOf("还可以，通过了。", "好的。", "勉强通过。")
    val RETRY = listOf("差一点，再读一次。", "不太对哦，再试一次。", "我们再来一遍。")
    val SKIP = listOf("没关系，我们跳过这个单词。", "这个有点难，我们换一个。", "不要紧，继续。")
    val SILENT = listOf("我没听清楚，请再读一次。", "好像没听到，再读一次好吗？")
    val ERROR = listOf("评分服务暂时不可用，我们跳过这个单词。", "网络好像有点问题，我们换一个。")

    /** 结束语（自然结束，按表现分级）。 */
    fun finale(matched: Int, total: Int): String {
        val rate = if (total == 0) 0.0 else matched.toDouble() / total
        return when {
            rate >= 0.8 -> "朗读练习结束。今天一共读了 ${total} 个单词，表现非常棒，继续保持！"
            rate >= 0.5 -> "朗读练习结束。今天一共读了 ${total} 个单词，表现不错，下次再见！"
            else -> "朗读练习结束。今天一共读了 ${total} 个单词，下次我们一起加油！"
        }
    }

    /** 提前结束（不计积分）。 */
    const val FINALE_EARLY = "练习已结束。本次没有获得积分，下次坚持到底哦。"

    /** 自学模式提前结束（自学本就不计积分，不提积分）。 */
    const val FINALE_EARLY_SELF = "练习已结束，下次继续。"
}

data class ReadingSessionUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val courseLabel: String = "",
    val courseId: String = "",
    val words: List<WordDto> = emptyList(),
    val currentIndex: Int = 0,
    val phase: ReadingPhase = ReadingPhase.LOADING,
    val practiceRound: Int = 0,        // WORD_PRACTICE 子轮次 (0=未进入, 1..3)
    val practiceRetry: Int = 0,        // v0.11.7：本轮领读后没跟读的重试次数（每次进 WORD_PRACTICE 清零）
    val finalAttempts: Int = 0,        // 最终朗读尝试次数 (0..MAX)
    val lastScore: Int? = null,
    val lastFeedback: FeedbackLevels = FeedbackLevels.NONE,
    val assessmentEnabled: Boolean? = null,
    val matchedCount: Int = 0,
    val skippedCount: Int = 0,
    val totalAttempts: Int = 0,
    val remainingSeconds: Int = TOTAL_SECONDS,
    val paused: Boolean = false,
    val finishing: Boolean = false,
    val finished: Boolean = false,
    val earnedPoints: Int = 0,
    val result: CourseExperienceResult? = null,
    val toast: String? = null,
    val showFinishConfirm: Boolean = false,
    // v0.12.2：任务模式（taskId != null 时启用，走 taskRepo.complete 而非 experience）
    val taskId: String? = null,
    val taskPoints: Int? = null,
    val canComplete: Boolean = false,
    // v0.16.0：家长自学 —— 正常跑 session,但结束不调 experience(无积分)
    val selfStudy: Boolean = false,
) {
    val currentWord: WordDto? get() = words.getOrNull(currentIndex)
    val totalWords: Int get() = words.size

    companion object {
        const val TOTAL_SECONDS = 20 * 60
        const val LISTEN_DURATION_MS = 3500L
        const val PRACTICE_LISTEN_DURATION_MS = 2000L  // v0.11.8：跟读检测录音时长（单词朗读 ~1.5s 够）
        const val PRACTICE_PASS_SCORE = 60             // v0.11.8：跟读 ISE 评分通过门槛（与最终朗读一致）
        const val MAX_PRACTICE_RETRY = 3               // v0.11.7：每轮最多重领读 3 次，超过强制进入下一轮
        const val MAX_FINAL_ATTEMPTS = 3   // 最终朗读最多 3 次，超过强制跳过
    }
}

@HiltViewModel
class ReadingSessionViewModel @Inject constructor(
    private val repo: CourseRepository,
    private val taskRepo: TaskRepository,
    val ttsPlayer: ReadingTtsPlayer,
) : ViewModel() {

    private val _ui = MutableStateFlow(ReadingSessionUiState())
    val ui: StateFlow<ReadingSessionUiState> = _ui.asStateFlow()

    private var timerJob: Job? = null
    private var listenJob: Job? = null
    private var transitionJob: Job? = null
    private var recorder: AudioRecorder? = null
    private val rng = Random(System.currentTimeMillis())

    fun load(courseId: String, selfStudy: Boolean = false) {
        if (_ui.value.courseId == courseId && _ui.value.selfStudy == selfStudy &&
            !_ui.value.loading
        ) return
        _ui.update {
            it.copy(
                loading = true, error = null, courseId = courseId,
                selfStudy = selfStudy,
                phase = ReadingPhase.LOADING,
            )
        }
        viewModelScope.launch {
            runCatching {
                val course = repo.get(courseId)
                val words = repo.listNextWords(courseId, limit = 20, mode = "adaptive")
                course to words
            }.onSuccess { (course, words) ->
                _ui.update {
                    it.copy(
                        loading = false,
                        courseLabel = "${course.textbook} · ${course.learningMethod}",
                        words = words,
                        currentIndex = 0,
                        practiceRound = 0,
                        finalAttempts = 0,
                        phase = ReadingPhase.INTRO_PHASE,
                    )
                }
                startTimer()
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    /**
     * v0.12.2：任务模式加载——和 load() 共用词表加载逻辑，但不启动 20 分钟 timer，
     * 并把 taskId / task.points 写到 state 供 finishTask() 使用。
     */
    fun loadForTask(courseId: String, taskId: String) {
        if (_ui.value.courseId == courseId && _ui.value.taskId == taskId && !_ui.value.loading) return
        _ui.update {
            it.copy(
                loading = true, error = null,
                courseId = courseId, taskId = taskId,
                phase = ReadingPhase.LOADING,
            )
        }
        viewModelScope.launch {
            runCatching {
                val course = repo.get(courseId)
                val words = repo.listNextWords(courseId, limit = 20, mode = "adaptive")
                val task = taskRepo.get(taskId)
                Triple(course, words, task)
            }.onSuccess { (course, words, task) ->
                _ui.update {
                    it.copy(
                        loading = false,
                        courseLabel = "${course.textbook} · ${course.learningMethod}",
                        words = words,
                        currentIndex = 0,
                        practiceRound = 0,
                        finalAttempts = 0,
                        phase = ReadingPhase.INTRO_PHASE,
                        taskPoints = task.points,
                    )
                }
                // 任务模式不启动倒计时
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    /**
     * TTS 阶段完成回调：根据当前 phase 推进状态机。
     * 由 Screen 在 TTS onDone 时调用。
     */
    fun onTtsDone() {
        val s = _ui.value
        when (s.phase) {
            ReadingPhase.INTRO_PHASE -> enterWordIntro(isFirstWord = true)
            ReadingPhase.WORD_INTRO -> scheduleTransition(500) { enterPractice(round = 1) }
            ReadingPhase.WORD_PRACTICE -> scheduleTransition(300) { startPracticeListening() }
            ReadingPhase.WORD_SPELL_READ -> startListening()
            ReadingPhase.FEEDBACK -> {
                when (s.lastFeedback) {
                    FeedbackLevels.EXCELLENT,
                    FeedbackLevels.GOOD,
                    FeedbackLevels.PASS,
                    FeedbackLevels.SKIP,
                    FeedbackLevels.ERROR -> advanceToNextWord()
                    FeedbackLevels.RETRY,
                    FeedbackLevels.SILENT -> retrySpellRead()
                    FeedbackLevels.NONE -> advanceToNextWord()
                }
            }
            else -> { /* LISTENING/ASSESSING/PRACTICE_LISTEN/PAUSED/FINISHED/LOADING/FAILED: 无 TTS */ }
        }
    }

    /** 用户点暂停 —— 冻结计时 + 停 TTS/录音 + 取消待推进 transition。 */
    fun pause() {
        val s = _ui.value
        if (s.phase == ReadingPhase.FINISHED || s.phase == ReadingPhase.FINALE) return
        timerJob?.cancel()
        listenJob?.cancel()
        transitionJob?.cancel()
        recorder?.stop()
        recorder = null
        ttsPlayer.stop()
        _ui.update { it.copy(paused = true, phase = ReadingPhase.PAUSED) }
    }

    /** 用户点继续 —— 从当前单词重新进入 WORD_INTRO（不重置 finalAttempts 让用户用原次数）。 */
    fun resume() {
        val s = _ui.value
        if (!s.paused) return
        _ui.update {
            it.copy(
                paused = false,
                phase = ReadingPhase.WORD_INTRO,
                practiceRound = 0,
            )
        }
        startTimer()
    }

    /** 用户点结束 —— 弹确认对话框。 */
    fun requestFinish() {
        val s = _ui.value
        if (s.finished || s.finishing) return
        timerJob?.cancel()
        listenJob?.cancel()
        transitionJob?.cancel()
        recorder?.stop()
        recorder = null
        ttsPlayer.stop()
        _ui.update { it.copy(paused = true, phase = ReadingPhase.PAUSED, showFinishConfirm = true) }
    }

    fun cancelFinish() {
        _ui.update {
            it.copy(showFinishConfirm = false, paused = false, phase = ReadingPhase.WORD_INTRO)
        }
        startTimer()
    }

    /** 确认提前结束 —— 直接 finishFlow，由 finished=true 触发 ResultDialog 显示 + TTS 播报。 */
    fun confirmFinishEarly() {
        _ui.update {
            it.copy(
                showFinishConfirm = false,
                paused = false,
            )
        }
        finishFlow(naturalEnding = false)
    }

    /** 进入 WORD_INTRO（实际由 Screen 触发 TTS）。 */
    private fun enterWordIntro(isFirstWord: Boolean) {
        _ui.update {
            it.copy(
                phase = ReadingPhase.WORD_INTRO,
                practiceRound = 0,
                finalAttempts = 0,
                lastScore = null,
            )
        }
    }

    /** 进入 WORD_PRACTICE 指定轮次（重置 retry）。 */
    private fun enterPractice(round: Int, resetRetry: Boolean = true) {
        _ui.update {
            it.copy(
                phase = ReadingPhase.WORD_PRACTICE,
                practiceRound = round,
                practiceRetry = if (resetRetry) 0 else it.practiceRetry,
            )
        }
    }

    /**
     * v0.11.8：跟读检测升级 —— WORD_PRACTICE TTS 完成后录 2s，上传 ISE 评分（read_word + spelling）。
     *  - score >= PRACTICE_PASS_SCORE (60) → 通过，进入下一轮或拼读
     *  - score < 60 + retry < MAX → retry++，TTS 提示"请跟我读一遍"后重领读（保持 round）
     *  - retry >= MAX → 强制进入下一轮（避免卡住）
     * 取代 v0.11.7 的 RMS 音量检测（用户随便发声就能绕过）。
     */
    private fun startPracticeListening() {
        val r = AudioRecorder()
        recorder = r
        val ok = r.start()
        if (!ok) {
            _ui.update {
                it.copy(phase = ReadingPhase.FAILED, error = "录音启动失败，请检查麦克风权限")
            }
            return
        }
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            _ui.update { it.copy(phase = ReadingPhase.WORD_PRACTICE_LISTEN) }
            delay(ReadingSessionUiState.PRACTICE_LISTEN_DURATION_MS)
            if (!isActive) return@launch
            val pcm = r.stop()
            recorder = null
            onPracticeRecordingComplete(pcm)
        }
    }

    private fun onPracticeRecordingComplete(pcm: ByteArray) {
        val s = _ui.value
        if (s.phase != ReadingPhase.WORD_PRACTICE_LISTEN) return
        val word = s.currentWord ?: run {
            _ui.update { it.copy(phase = ReadingPhase.FAILED, error = "无当前单词") }
            return
        }
        _ui.update { it.copy(phase = ReadingPhase.ASSESSING_PRACTICE) }
        viewModelScope.launch {
            runCatching {
                repo.assessWord(
                    courseId = s.courseId,
                    wordId = word.id,
                    audio = pcm,
                    refTextOverride = word.spelling,
                    category = "read_word",
                )
            }.onSuccess { res ->
                handlePracticeAssessment(res)
            }.onFailure { e ->
                _ui.update {
                    it.copy(
                        phase = ReadingPhase.WORD_PRACTICE,
                        practiceRetry = it.practiceRetry + 1,
                        toast = "评分失败，再试一次",
                        error = friendlyError(e),
                    )
                }
            }
        }
    }

    private fun handlePracticeAssessment(res: WordAssessmentResult) {
        val s = _ui.value
        if (!res.enabled) {
            // ISE 未配置 → 不可达路径（已部署 ISE），但保留兜底直接进下一轮
            val nextRound = s.practiceRound + 1
            scheduleTransition(300) {
                if (nextRound <= 3) enterPractice(round = nextRound) else enterSpellRead()
            }
            return
        }
        if (res.score >= ReadingSessionUiState.PRACTICE_PASS_SCORE) {
            // 通过：进下一轮或拼读
            val nextRound = s.practiceRound + 1
            _ui.update { it.copy(lastScore = res.score) }
            scheduleTransition(300) {
                if (nextRound <= 3) enterPractice(round = nextRound) else enterSpellRead()
            }
        } else {
            // 分数低 → 重领读或强制跳过
            val nextRetry = s.practiceRetry + 1
            if (nextRetry > ReadingSessionUiState.MAX_PRACTICE_RETRY) {
                _ui.update {
                    it.copy(
                        phase = ReadingPhase.WORD_PRACTICE,
                        practiceRetry = 0,
                        lastScore = res.score,
                        toast = "我们继续下一个",
                    )
                }
                val nextRound = s.practiceRound + 1
                scheduleTransition(500) {
                    if (nextRound <= 3) enterPractice(round = nextRound) else enterSpellRead()
                }
            } else {
                _ui.update {
                    it.copy(
                        phase = ReadingPhase.WORD_PRACTICE,
                        practiceRetry = nextRetry,
                        lastScore = res.score,
                        toast = "请跟我读一遍",
                    )
                }
            }
        }
    }

    private fun enterSpellRead() {
        _ui.update { it.copy(phase = ReadingPhase.WORD_SPELL_READ) }
    }

    private fun retrySpellRead() {
        _ui.update {
            it.copy(
                phase = ReadingPhase.WORD_SPELL_READ,
                finalAttempts = it.finalAttempts + 1,
            )
        }
    }

    private fun scheduleTransition(delayMs: Long, action: () -> Unit) {
        transitionJob?.cancel()
        transitionJob = viewModelScope.launch {
            delay(delayMs)
            if (!isActive) return@launch
            action()
        }
    }

    private fun startListening() {
        val r = AudioRecorder()
        recorder = r
        val ok = r.start()
        if (!ok) {
            _ui.update {
                it.copy(
                    phase = ReadingPhase.FAILED,
                    error = "录音启动失败，请检查麦克风权限",
                )
            }
            return
        }
        // TTS onDone 后等 250ms 让扬声器余音衰减干净
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            delay(250)
            _ui.update { it.copy(phase = ReadingPhase.LISTENING) }
            delay(ReadingSessionUiState.LISTEN_DURATION_MS)
            if (!isActive) return@launch
            val pcm = r.stop()
            recorder = null
            onRecordingComplete(pcm)
        }
    }

    private fun onRecordingComplete(pcm: ByteArray) {
        val s = _ui.value
        if (s.phase != ReadingPhase.LISTENING) return
        _ui.update {
            it.copy(
                phase = ReadingPhase.ASSESSING,
                finalAttempts = it.finalAttempts + 1,
                totalAttempts = it.totalAttempts + 1,
            )
        }
        val word = s.currentWord ?: run {
            _ui.update { it.copy(phase = ReadingPhase.FAILED, error = "无当前单词") }
            return
        }
        // 多行 paper：D\nO\nG\ndog（字母逐行 + 单词一行）
        // ISE read_word + 多行 paper 会评估整段录音，字母部分测连贯性，单词部分测准确度
        val refText = VoiceLines.spellReadRefText(word.spelling)
        viewModelScope.launch {
            runCatching {
                repo.assessWord(
                    courseId = s.courseId,
                    wordId = word.id,
                    audio = pcm,
                    refTextOverride = refText,
                    category = "read_word",
                )
            }
                .onSuccess { res -> handleAssessment(res) }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            lastFeedback = FeedbackLevels.ERROR,
                            phase = ReadingPhase.FEEDBACK,
                            error = friendlyError(e),
                        )
                    }
                }
        }
    }

    private fun handleAssessment(res: WordAssessmentResult) {
        _ui.update {
            it.copy(
                assessmentEnabled = res.enabled,
                lastScore = if (res.enabled) res.score else null,
            )
        }
        if (!res.enabled) {
            // ISE 未配置 —— 不可达路径（已部署 ISE），但保留兜底
            _ui.update {
                it.copy(
                    lastFeedback = FeedbackLevels.PASS,
                    phase = ReadingPhase.FEEDBACK,
                    matchedCount = it.matchedCount + 1,
                )
            }
            return
        }
        val attempts = _ui.value.finalAttempts
        val level = when {
            res.score >= 90 -> FeedbackLevels.EXCELLENT
            res.score >= 75 -> FeedbackLevels.GOOD
            res.score >= 60 -> FeedbackLevels.PASS
            attempts >= ReadingSessionUiState.MAX_FINAL_ATTEMPTS -> FeedbackLevels.SKIP
            else -> FeedbackLevels.RETRY
        }
        _ui.update {
            val matchedDelta = if (level == FeedbackLevels.EXCELLENT ||
                level == FeedbackLevels.GOOD || level == FeedbackLevels.PASS) 1 else 0
            val skippedDelta = if (level == FeedbackLevels.SKIP) 1 else 0
            val newMatched = it.matchedCount + matchedDelta
            it.copy(
                lastFeedback = level,
                phase = ReadingPhase.FEEDBACK,
                matchedCount = newMatched,
                skippedCount = it.skippedCount + skippedDelta,
                // v0.12.2 任务模式：答对至少 1 词即可完成
                canComplete = it.taskId != null && newMatched >= 1,
            )
        }
    }

    private fun advanceToNextWord() {
        val s = _ui.value
        if (s.words.isEmpty()) return
        val nextIdx = if (s.currentIndex + 1 >= s.words.size) 0 else s.currentIndex + 1
        _ui.update {
            it.copy(
                currentIndex = nextIdx,
                practiceRound = 0,
                finalAttempts = 0,
                lastScore = null,
                phase = ReadingPhase.WORD_INTRO,
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val s = _ui.value
                if (s.finished || s.finishing || s.paused) break
                if (s.remainingSeconds <= 0) {
                    // 时间到 → 自然结束，直接 finishFlow
                    transitionJob?.cancel()
                    listenJob?.cancel()
                    recorder?.stop()
                    recorder = null
                    ttsPlayer.stop()
                    finishFlow(naturalEnding = true)
                    break
                }
                delay(1000)
                _ui.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
            }
        }
    }

    /** FINALE 阶段 TTS 完成后真正调积分（自学模式跳过 experience，直接结束）。 */
    private fun finishFlow(naturalEnding: Boolean) {
        val s = _ui.value
        if (s.finishing || s.finished) return
        _ui.update { it.copy(finishing = true) }
        timerJob?.cancel()
        listenJob?.cancel()
        viewModelScope.launch {
            if (naturalEnding && !s.selfStudy) {
                runCatching { repo.experience(s.courseId) }
                    .onSuccess { r ->
                        _ui.update {
                            it.copy(
                                finishing = false,
                                finished = true,
                                phase = ReadingPhase.FINISHED,
                                result = r,
                                earnedPoints = r.pointsEarned,
                            )
                        }
                    }
                    .onFailure { e ->
                        _ui.update {
                            it.copy(
                                finishing = false,
                                finished = true,
                                phase = ReadingPhase.FINISHED,
                                error = friendlyError(e),
                            )
                        }
                    }
            } else {
                _ui.update {
                    it.copy(
                        finishing = false,
                        finished = true,
                        phase = ReadingPhase.FINISHED,
                        earnedPoints = 0,
                    )
                }
            }
        }
    }

    /**
     * v0.12.2 任务模式：用户点「完成」后调 taskRepo.complete(taskId)，
     * 后端 complete_task 会校验任务可完成性 + 自动加积分。
     * 调成功后置 finished=true，由 Screen 层在 onBack 时回写 savedStateHandle 通知 TaskDetail 刷新。
     */
    fun finishTask() {
        val s = _ui.value
        val taskId = s.taskId ?: return
        if (s.finishing || s.finished) return
        if (!s.canComplete) {
            _ui.update { it.copy(toast = "至少答对 1 个单词才能完成") }
            return
        }
        _ui.update { it.copy(finishing = true) }
        timerJob?.cancel()
        listenJob?.cancel()
        ttsPlayer.stop()
        viewModelScope.launch {
            runCatching { taskRepo.complete(taskId) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            finishing = false,
                            finished = true,
                            phase = ReadingPhase.FINISHED,
                            earnedPoints = s.taskPoints ?: 0,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            finishing = false,
                            finished = true,
                            phase = ReadingPhase.FINISHED,
                            error = friendlyError(e),
                        )
                    }
                }
        }
    }

    // ===== TTS 文案生成（Screen 触发 TTS 时调） =====

    fun introLine(): String = VoiceLines.INTRO

    fun wordIntroLine(): String {
        val w = _ui.value.currentWord ?: return ""
        return VoiceLines.wordIntro(
            spelling = w.spelling,
            meaningCn = w.meaningCn,
            syllables = w.syllables,
            sampleSentence = w.sampleSentence,
            sampleSentenceTranslation = w.sampleSentenceTranslation,
        )
    }

    fun practiceLine(): String {
        val s = _ui.value
        val w = s.currentWord ?: return ""
        // v0.11.7：跟读检测未通过时，前缀换成"请跟我读一遍"，让用户听出"刚才没读"
        if (s.practiceRetry > 0) {
            return "请跟我读一遍，${w.spelling}"
        }
        return VoiceLines.practice(w.spelling, s.practiceRound)
    }

    fun spellReadLine(): String {
        val w = _ui.value.currentWord ?: return ""
        return VoiceLines.spellAndRead(w.spelling)
    }

    fun feedbackLine(): String {
        val level = _ui.value.lastFeedback
        return when (level) {
            FeedbackLevels.EXCELLENT -> VoiceLines.EXCELLENT.random(rng)
            FeedbackLevels.GOOD -> VoiceLines.GOOD.random(rng)
            FeedbackLevels.PASS -> VoiceLines.PASS.random(rng)
            FeedbackLevels.RETRY -> VoiceLines.RETRY.random(rng)
            FeedbackLevels.SKIP -> VoiceLines.SKIP.random(rng)
            FeedbackLevels.SILENT -> VoiceLines.SILENT.random(rng)
            FeedbackLevels.ERROR -> VoiceLines.ERROR.random(rng)
            FeedbackLevels.NONE -> ""
        }
    }

    fun finaleLine(): String {
        val s = _ui.value
        return if (s.lastFeedback == FeedbackLevels.ERROR) {
            if (s.selfStudy) VoiceLines.FINALE_EARLY_SELF else VoiceLines.FINALE_EARLY
        } else {
            VoiceLines.finale(matched = s.matchedCount, total = s.matchedCount + s.skippedCount)
        }
    }

    fun clearToast() {
        _ui.update { it.copy(toast = null) }
    }

    override fun onCleared() {
        timerJob?.cancel()
        listenJob?.cancel()
        transitionJob?.cancel()
        recorder?.destroy()
        recorder = null
        ttsPlayer.release()
        super.onCleared()
    }
}
