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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.CourseSessionType
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenSession: (type: CourseSessionType, courseId: String, taskId: String) -> Unit,
    refreshKey: Boolean = false,
    vm: TaskDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(taskId, refreshKey) { vm.load(taskId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it) }
    }

    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除该任务？") },
            text = { Text("此操作不可撤销") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(taskId) { onBack() }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.task?.title ?: "任务详情",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (state.isParent && state.task != null) {
                        IconButton(onClick = { onEdit(taskId) }) { Icon(Icons.Filled.Edit, "编辑") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "删除") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loading || state.task == null) {
                LoadingState()
            } else {
                val t = state.task!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsCard {
                        SettingsRow(
                            title = "课程",
                            trailing = {
                                ValueText(t.course?.let { "${it.subject} · ${it.textbook} · ${it.learningMethod}" } ?: "无")
                            },
                            showDivider = true,
                        )
                        SettingsRow(
                            title = "积分",
                            trailing = { ValueText("${t.points}") },
                            showDivider = true,
                        )
                        SettingsRow(
                            title = "指派给",
                            trailing = { ValueText(t.assigneeUsername ?: "任意孩子") },
                            showDivider = true,
                        )
                        SettingsRow(
                            title = "重复",
                            trailing = { ValueText(recurrenceText(t)) },
                            showDivider = true,
                        )
                        SettingsRow(
                            title = "可完成日期",
                            trailing = {
                                ValueText(
                                    if (t.availableStartDate != null || t.availableEndDate != null) {
                                        "${t.availableStartDate ?: "…"} ~ ${t.availableEndDate ?: "…"}"
                                    } else "不限"
                                )
                            },
                            showDivider = true,
                        )
                        SettingsRow(
                            title = "每日时段",
                            trailing = {
                                ValueText(
                                    if (t.availableStartTime != null && t.availableEndTime != null) {
                                        "${t.availableStartTime.take(5)} ~ ${t.availableEndTime.take(5)}"
                                    } else "全天"
                                )
                            },
                            showDivider = true,
                        )
                        SettingsRow(
                            title = "截止日期",
                            trailing = { ValueText(t.dueDate ?: "无") },
                            showDivider = true,
                        )
                        SettingsRow(
                            title = "状态",
                            trailing = {
                                ValueText(if (t.isActive) "启用中" else "已停用")
                            },
                            showDivider = false,
                        )
                    }
                    t.description?.let { desc ->
                        SettingsCard {
                            SettingsRow(
                                title = "描述",
                                subtitle = desc,
                                showDivider = false,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val sessionType = state.sessionType
                    val showSessionEntry = sessionType != null && t.courseId != null && state.hasWords == true
                    if (state.completed) {
                        SettingsCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    if (t.recurrenceType == "one_off") "已完成" else "今日已完成",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else if (state.isParent) {
                        // 家长视角：不显示完成按钮，仅展示只读状态
                        SettingsCard {
                            SettingsRow(
                                title = if (t.assigneeUsername != null)
                                    "等待 ${t.assigneeUsername} 完成"
                                else "等待孩子完成",
                                subtitle = "完成状态只能由孩子本人触发",
                                showDivider = false,
                            )
                        }
                    } else if (showSessionEntry) {
                        // 互动课程任务且有词表：强制走对应 session 流程（朗读/学习/测评）
                        val actionLabel = when (sessionType) {
                            CourseSessionType.READING -> "开始朗读"
                            CourseSessionType.LEARN -> "开始学习"
                            CourseSessionType.QUIZ -> "开始测评"
                            else -> "开始学习"
                        }
                        Button(
                            onClick = { onOpenSession(sessionType!!, t.courseId!!, t.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("$actionLabel · +${t.points} 积分")
                        }
                    } else if (t.isActive) {
                        Button(
                            onClick = { vm.complete(t.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("标记完成 · +${t.points} 积分")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueText(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val WEEKDAY_NAMES = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")

private fun recurrenceText(t: com.myhome.net.dto.TaskDto): String = when (t.recurrenceType) {
    "daily" -> "每天"
    "weekly" -> {
        val days = t.recurrenceWeekdays?.sorted()?.mapNotNull { WEEKDAY_NAMES[it] }?.joinToString("")
        if (days.isNullOrEmpty()) "每周" else "每周$days"
    }
    else -> "一次性"
}
