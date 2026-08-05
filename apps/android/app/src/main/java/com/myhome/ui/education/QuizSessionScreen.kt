package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.WordDto
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSessionScreen(
    courseId: String,
    onBack: () -> Unit,
    taskId: String? = null,
    onTaskCompleted: (() -> Unit)? = null,
    selfStudy: Boolean = false,
    vm: QuizSessionViewModel = hiltViewModel(),
) {
    // 任务模式（taskId != null）走 loadForTask；自学走 loadSelfStudy；体验模式走 load
    LaunchedEffect(courseId, taskId, selfStudy) {
        when {
            taskId != null -> vm.loadForTask(courseId, taskId)
            selfStudy -> vm.loadSelfStudy(courseId)
            else -> vm.load(courseId)
        }
    }
    val state by vm.ui.collectAsStateWithLifecycle()
    val isTaskMode = taskId != null

    // 任务模式 finishTask 成功后通知上一页刷新
    LaunchedEffect(state.finished, state.taskId, state.error) {
        if (isTaskMode && state.finished && state.taskId != null && state.error == null) {
            onTaskCompleted?.invoke()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHostState.showSnackbar(it); vm.clearToast() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.courseLabel.ifBlank { if (isTaskMode) "测评任务" else "测评课程" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.totalWords > 0 &&
                            (state.phase == QuizPhase.QUESTION || state.phase == QuizPhase.FEEDBACK)
                        ) {
                            Text(
                                text = "第 ${state.currentIndex + 1}/${state.totalWords} 题",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.words.isEmpty() -> ErrorState(
                    state.error!!,
                    onRetry = {
                        when {
                            taskId != null -> vm.loadForTask(courseId, taskId)
                            selfStudy -> vm.loadSelfStudy(courseId)
                            else -> vm.load(courseId)
                        }
                    },
                )
                else -> when (state.phase) {
                    QuizPhase.QUESTION -> state.currentWord?.let { word ->
                        QuizQuestionCard(
                            word = word,
                            input = state.answerInput,
                            submitting = state.submitting,
                            onInputChange = vm::onAnswerInputChange,
                            onSpeak = { vm.ttsPlayer.speak(it) {} },
                            onSubmit = vm::submitAnswer,
                        )
                    }
                    QuizPhase.FEEDBACK -> state.currentWord?.let { word ->
                        QuizFeedbackCard(word = word, correct = state.lastCorrect)
                    }
                    QuizPhase.REPORT -> QuizReportCard(
                        state = state,
                        isTaskMode = isTaskMode,
                        selfStudy = selfStudy,
                        onFinishTask = vm::finishTask,
                        onBack = onBack,
                    )
                    else -> {}
                }
            }
        }
    }

    if (state.finished) {
        QuizResultDialog(state = state, onBack = onBack)
    }
}

@Composable
private fun QuizQuestionCard(
    word: WordDto,
    input: String,
    submitting: Boolean,
    onInputChange: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "根据中文意思拼写单词",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = word.meaningCn?.takeIf { it.isNotBlank() } ?: "（无释义）",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onSpeak(word.spelling) }) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "播放发音",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            SpellingInputDisplay(input)
        }
        SpellingKeyboard(
            onKey = { if (input.length < 30) onInputChange(input + it) },
            onBackspace = { if (input.isNotEmpty()) onInputChange(input.dropLast(1)) },
            enabled = !submitting,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSubmit,
            enabled = input.isNotBlank() && !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(48.dp),
        ) {
            Text(if (submitting) "提交中…" else "提交")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QuizFeedbackCard(
    word: WordDto,
    correct: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (correct) Icons.Filled.CheckCircle else Icons.Filled.Close,
            contentDescription = null,
            tint = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (correct) "回答正确" else "回答错误",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!correct) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = word.spelling,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun QuizReportCard(
    state: QuizSessionUiState,
    isTaskMode: Boolean,
    selfStudy: Boolean,
    onFinishTask: () -> Unit,
    onBack: () -> Unit,
) {
    val total = state.results.size
    val correct = state.correctCount
    val rate = if (total == 0) 0 else correct * 100 / total
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "测评报告",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "答对 $correct/$total · 正确率 $rate%",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.results) { r ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (r.correct) Icons.Filled.CheckCircle else Icons.Filled.Close,
                            contentDescription = if (r.correct) "正确" else "错误",
                            tint = if (r.correct) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = r.spelling,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        r.meaningCn?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (isTaskMode) {
            Button(
                onClick = onFinishTask,
                enabled = !state.finishing && !state.finished,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (state.finishing) "提交中…" else "完成任务 · +${state.taskPoints ?: 0} 积分")
            }
        } else if (selfStudy) {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("完成")
            }
        } else if (!state.finished) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "积分结算中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuizResultDialog(
    state: QuizSessionUiState,
    onBack: () -> Unit,
) {
    val isTaskMode = state.taskId != null
    val natural = state.earnedPoints > 0
    val total = state.results.size
    val correct = state.correctCount
    val rate = if (total == 0) 0 else correct * 100 / total
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(
                if (natural) "测评完成" else "已结束",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("答对: $correct/$total")
                Text("正确率: $rate%")
                if (natural) {
                    if (!isTaskMode && state.result != null) {
                        Text("孩子: ${state.result!!.childUsername}")
                    }
                    Text(
                        "积分: +${state.earnedPoints}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        "本次未获得积分",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("返回") } },
    )
}
