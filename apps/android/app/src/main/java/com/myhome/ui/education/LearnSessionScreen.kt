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
fun LearnSessionScreen(
    courseId: String,
    onBack: () -> Unit,
    taskId: String? = null,
    onTaskCompleted: (() -> Unit)? = null,
    selfStudy: Boolean = false,
    vm: LearnSessionViewModel = hiltViewModel(),
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

    // 进入学习卡自动播放单词发音
    LaunchedEffect(state.phase, state.currentIndex) {
        if (state.phase == LearnPhase.STUDY) {
            state.currentWord?.let { vm.ttsPlayer.speak(it.spelling) {} }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.courseLabel.ifBlank { if (isTaskMode) "学习任务" else "学习课程" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.totalWords > 0 &&
                            state.phase != LearnPhase.SUMMARY && state.phase != LearnPhase.LOADING
                        ) {
                            Text(
                                text = "第 ${state.currentIndex + 1}/${state.totalWords} 词",
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
                    LearnPhase.STUDY -> state.currentWord?.let { word ->
                        StudyCard(
                            word = word,
                            onSpeak = { vm.ttsPlayer.speak(it) {} },
                            onStartSpell = vm::startSpell,
                        )
                    }
                    LearnPhase.SPELL -> state.currentWord?.let { word ->
                        SpellCard(
                            word = word,
                            input = state.spellInput,
                            submitting = state.submitting,
                            onInputChange = vm::onSpellInputChange,
                            onSpeak = { vm.ttsPlayer.speak(it) {} },
                            onSubmit = vm::submitSpelling,
                        )
                    }
                    LearnPhase.FEEDBACK -> state.currentWord?.let { word ->
                        LearnFeedbackCard(
                            word = word,
                            correct = state.lastCorrect,
                            isLast = state.currentIndex + 1 >= state.totalWords,
                            onSpeak = { vm.ttsPlayer.speak(it) {} },
                            onNext = vm::nextWord,
                        )
                    }
                    LearnPhase.SUMMARY -> LearnSummaryCard(
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
        LearnResultDialog(state = state, onBack = onBack)
    }
}

@Composable
private fun StudyCard(
    word: WordDto,
    onSpeak: (String) -> Unit,
    onStartSpell: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.spelling,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { onSpeak(word.spelling) }) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "播放单词",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        word.phonetic?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        word.meaningCn?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        word.sampleSentence?.takeIf { it.isNotBlank() }?.let { sentence ->
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sentence,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onSpeak(sentence) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "播放例句",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    word.sampleSentenceTranslation?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStartSpell,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("我会了，考考我")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SpellCard(
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
private fun LearnFeedbackCard(
    word: WordDto,
    correct: Boolean,
    isLast: Boolean,
    onSpeak: (String) -> Unit,
    onNext: () -> Unit,
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
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (correct) "拼写正确！" else "拼写错误",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.spelling,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { onSpeak(word.spelling) }) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "播放单词",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (!correct) {
            Text(
                text = "记住正确拼写，继续加油",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(if (isLast) "查看学习成果" else "下一个")
        }
    }
}

@Composable
private fun LearnSummaryCard(
    state: LearnSessionUiState,
    isTaskMode: Boolean,
    selfStudy: Boolean,
    onFinishTask: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "学习成果",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "新学 ${state.totalWords} 个单词",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "拼写正确 ${state.correctCount} 个 · 错误 ${state.wrongCount} 个",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(32.dp))
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
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = "积分结算中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LearnResultDialog(
    state: LearnSessionUiState,
    onBack: () -> Unit,
) {
    val isTaskMode = state.taskId != null
    val natural = state.earnedPoints > 0
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(
                if (natural) "学习完成" else "已结束",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("新学单词: ${state.totalWords} 个")
                Text("拼写正确: ${state.correctCount} 个")
                if (state.wrongCount > 0) {
                    Text("拼写错误: ${state.wrongCount} 个")
                }
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
