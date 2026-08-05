package com.myhome.ui.education

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSessionScreen(
    courseId: String,
    onBack: () -> Unit,
    taskId: String? = null,
    onTaskCompleted: (() -> Unit)? = null,
    selfStudy: Boolean = false,
    vm: ReadingSessionViewModel = hiltViewModel(),
) {
    // 任务模式（taskId != null）走 loadForTask；体验/自学模式走 load（自学不结算积分）
    LaunchedEffect(courseId, taskId, selfStudy) {
        if (taskId != null) vm.loadForTask(courseId, taskId) else vm.load(courseId, selfStudy)
    }
    val state by vm.ui.collectAsStateWithLifecycle()
    val isTaskMode = taskId != null

    // 任务模式 finishTask 成功后通知上一页刷新（ResultDialog 仍由用户手动点返回关闭）
    LaunchedEffect(state.finished, state.taskId, state.error) {
        if (isTaskMode && state.finished && state.taskId != null && state.error == null) {
            onTaskCompleted?.invoke()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHostState.showSnackbar(it); vm.clearToast() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    val context = LocalContext.current
    var hasMicPermission by remember {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(granted)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // phase 变化触发 TTS：每次进入新阶段或新轮次/新词时调对应文案
    LaunchedEffect(
        state.phase,
        state.currentIndex,
        state.practiceRound,
        state.practiceRetry,
        state.finalAttempts,
        state.lastFeedback,
    ) {
        when (state.phase) {
            ReadingPhase.INTRO_PHASE -> vm.ttsPlayer.speak(vm.introLine()) { vm.onTtsDone() }
            ReadingPhase.WORD_INTRO -> vm.ttsPlayer.speak(vm.wordIntroLine()) { vm.onTtsDone() }
            ReadingPhase.WORD_PRACTICE -> vm.ttsPlayer.speak(vm.practiceLine()) { vm.onTtsDone() }
            ReadingPhase.WORD_SPELL_READ -> vm.ttsPlayer.speak(vm.spellReadLine()) { vm.onTtsDone() }
            ReadingPhase.FEEDBACK -> {
                val line = vm.feedbackLine()
                if (line.isNotBlank()) vm.ttsPlayer.speak(line) { vm.onTtsDone() }
            }
            // FINALE 不再走这里：finished=true 后由下面的 LaunchedEffect(finished) 触发 TTS
            // WORD_PRACTICE_LISTEN / LISTENING / ASSESSING / PAUSED / FINISHED / LOADING / FAILED: 无 TTS
            else -> {}
        }
    }

    // v0.11.7：finished=true 时显示 ResultDialog 同时播报结束语
    // 用户诉求："跳弹窗后播报" —— ResultDialog 由 finished=true 渲染，先于 TTS 启动
    LaunchedEffect(state.finished) {
        if (state.finished) {
            vm.ttsPlayer.speak(vm.finaleLine()) { /* no-op: ResultDialog 已显示，TTS 只是配音 */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.courseLabel.ifBlank { if (isTaskMode) "朗读任务" else "朗读练习" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.totalWords > 0) {
                            Text(
                                text = "已通过 ${state.matchedCount} · 跳过 ${state.skippedCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isTaskMode) {
                        // 任务模式：完成按钮（答对 >=1 词后启用）
                        TextButton(
                            onClick = vm::finishTask,
                            enabled = state.canComplete && !state.finishing && !state.finished,
                        ) {
                            Text(
                                if (state.finishing) "提交中" else "完成",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        // 体验模式：保留 timer + 暂停 + 结束
                        Text(
                            text = formatTime(state.remainingSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.remainingSeconds <= 60)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        IconButton(
                            onClick = { if (state.paused) vm.resume() else vm.pause() },
                            enabled = state.phase != ReadingPhase.FINISHED && state.phase != ReadingPhase.FINALE,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (state.paused) "继续" else "暂停",
                            )
                        }
                        IconButton(
                            onClick = vm::requestFinish,
                            enabled = state.phase != ReadingPhase.FINISHED && state.phase != ReadingPhase.FINALE,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "结束")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            state.error != null && state.words.isEmpty() -> ErrorState(
                state.error!!,
                onRetry = { vm.load(courseId) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            state.words.isEmpty() -> EmptyState(
                title = "本课程暂无单词",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            !hasMicPermission -> NoMicPermission(
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onBack = onBack,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> SessionContent(
                state = state,
                onResume = vm::resume,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    // 提前结束确认对话框
    if (state.showFinishConfirm) {
        AlertDialog(
            onDismissRequest = vm::cancelFinish,
            title = { Text("提前结束练习？", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    if (state.selfStudy) {
                        "已通过 ${state.matchedCount} 个单词。确认结束吗？"
                    } else {
                        "时间还没到，本次练习不会获得任何积分。" +
                            "已通过 ${state.matchedCount} 个单词不会计入奖励。确认结束吗？"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmFinishEarly,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelFinish) { Text("继续练习") }
            },
        )
    }

    // 完成结算对话框
    if (state.finished) {
        ResultDialog(state = state, onBack = onBack)
    }
}

@Composable
private fun SessionContent(
    state: ReadingSessionUiState,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val word = state.currentWord
    if (word == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { Text("无单词") }
        return
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PhaseBadge(state = state)

            Spacer(Modifier.height(28.dp))

            // 大字单词
            Text(
                text = word.spelling,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            // v0.11.8: IPA 音标展示（英式标准发音）
            if (!word.phonetic.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = word.phonetic,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            if (word.syllables.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = word.syllables.joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (!word.meaningCn.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = word.meaningCn,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            // v0.11.8: 例句 + 例句中文翻译展示（朗读阶段才显示，避免一开始太满）
            val showSentence = word.sampleSentence != null && state.phase in setOf(
                ReadingPhase.WORD_INTRO,
                ReadingPhase.WORD_PRACTICE,
                ReadingPhase.WORD_PRACTICE_LISTEN,
                ReadingPhase.ASSESSING_PRACTICE,
                ReadingPhase.WORD_SPELL_READ,
                ReadingPhase.LISTENING,
                ReadingPhase.ASSESSING,
                ReadingPhase.FEEDBACK,
            )
            if (showSentence) {
                Spacer(Modifier.height(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = word.sampleSentence,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    if (!word.sampleSentenceTranslation.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = word.sampleSentenceTranslation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            ScoreRow(state = state)

            Spacer(Modifier.height(36.dp))

            ListeningIndicator(state = state)
        }

        // 暂停遮罩（仅在单纯暂停态显示，结束确认时不挡对话框）
        AnimatedVisibility(
            visible = state.paused && !state.showFinishConfirm,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PauseOverlay(onResume = onResume)
        }
    }
}

@Composable
private fun PhaseBadge(state: ReadingSessionUiState) {
    val (text, color, icon) = when (state.phase) {
        ReadingPhase.INTRO_PHASE -> Triple("准备开始...", MaterialTheme.colorScheme.primary, Icons.AutoMirrored.Filled.VolumeUp)
        ReadingPhase.WORD_INTRO -> Triple("单词讲解", MaterialTheme.colorScheme.primary, Icons.AutoMirrored.Filled.VolumeUp)
        ReadingPhase.WORD_PRACTICE -> Triple("跟我读 · 第 ${state.practiceRound} / 3 次", MaterialTheme.colorScheme.primary, Icons.AutoMirrored.Filled.VolumeUp)
        ReadingPhase.WORD_PRACTICE_LISTEN -> Triple("请跟读...", MaterialTheme.colorScheme.tertiary, Icons.Filled.Mic)
        ReadingPhase.ASSESSING_PRACTICE -> Triple("跟读评分中...", MaterialTheme.colorScheme.primary, Icons.Filled.Mic)
        ReadingPhase.WORD_SPELL_READ -> Triple(
            if (state.finalAttempts > 0) "拼读 + 朗读 · 第 ${state.finalAttempts} 次"
            else "拼读 + 朗读 · 请跟读",
            MaterialTheme.colorScheme.tertiary, Icons.Filled.Mic,
        )
        ReadingPhase.LISTENING -> Triple("请朗读...", MaterialTheme.colorScheme.tertiary, Icons.Filled.Mic)
        ReadingPhase.ASSESSING -> Triple("评分中...", MaterialTheme.colorScheme.primary, Icons.Filled.Mic)
        ReadingPhase.FEEDBACK -> Triple("...", MaterialTheme.colorScheme.primary, null)
        ReadingPhase.PAUSED -> Triple("已暂停", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Pause)
        ReadingPhase.FINALE -> Triple("练习结束", MaterialTheme.colorScheme.primary, null)
        ReadingPhase.FINISHED -> Triple("已完成", MaterialTheme.colorScheme.primary, null)
        ReadingPhase.FAILED -> Triple("出错", MaterialTheme.colorScheme.error, null)
        ReadingPhase.LOADING -> Triple("加载中...", MaterialTheme.colorScheme.onSurfaceVariant, null)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.phase == ReadingPhase.ASSESSING || state.phase == ReadingPhase.ASSESSING_PRACTICE) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = color,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
private fun ScoreRow(state: ReadingSessionUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.lastScore != null) {
            val scoreColor = when {
                state.lastScore >= 75 -> MaterialTheme.colorScheme.primary
                state.lastScore >= 60 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            Text(
                text = "${state.lastScore} 分",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scoreColor,
            )
        }
        if (state.finalAttempts > 0 && state.phase != ReadingPhase.FINISHED) {
            Text(
                text = "拼读第 ${state.finalAttempts} / ${ReadingSessionUiState.MAX_FINAL_ATTEMPTS} 次",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListeningIndicator(state: ReadingSessionUiState) {
    val isInListening = state.phase == ReadingPhase.LISTENING ||
        state.phase == ReadingPhase.WORD_SPELL_READ ||
        state.phase == ReadingPhase.WORD_PRACTICE_LISTEN
    val transition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    Surface(
        shape = CircleShape,
        color = if (isInListening) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = if (isInListening) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .scale(if (isInListening) scale else 1f),
            )
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.Pause,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "已暂停",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "点击继续，从中断处继续练习",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onResume, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("继续练习")
            }
        }
    }
}

@Composable
private fun NoMicPermission(
    onRequest: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(28.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("需要麦克风权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "朗读练习需要录音你的发音上传到讯飞语音评测服务进行评分。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRequest) { Text("授予权限") }
            OutlinedButton(onClick = onBack) { Text("返回") }
        }
    }
}

@Composable
private fun ResultDialog(
    state: ReadingSessionUiState,
    onBack: () -> Unit,
) {
    val isTaskMode = state.taskId != null
    val natural = state.earnedPoints > 0
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(
                if (isTaskMode && natural) "朗读完成"
                else if (natural) "练习完成"
                else if (state.selfStudy) "练习完成"
                else "已结束",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("通过: ${state.matchedCount} 个")
                Text("跳过: ${state.skippedCount} 个")
                Text("总尝试: ${state.totalAttempts} 次")
                if (state.selfStudy) {
                    // 自学不提积分
                } else if (isTaskMode) {
                    if (natural) {
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
                } else {
                    if (natural && state.result != null) {
                        Text("孩子: ${state.result!!.childUsername}")
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
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("返回") } },
    )
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
