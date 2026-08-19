package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.GradeDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

/**
 * 学科成绩列表（v0.17.0）。
 * 家长：顶部成员过滤 chips + 汇总卡 + 按学科分组 + FAB 录入；孩子：只看自己。
 */
@Composable
fun GradeListScreen(
    isParent: Boolean,
    onBack: () -> Unit,
    onOpenForm: (String?) -> Unit,
    vm: GradeListViewModel = hiltViewModel(),
) {
    LaunchedEffect(isParent) { vm.refresh(isParent) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbarHost.showSnackbar(it); vm.consumeToast() } }
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it) } }

    // 内存过滤：家长按选中成员过滤
    val visible = if (state.isParent && state.selectedUserId != null) {
        state.grades.filter { it.assigneeUserId == state.selectedUserId }
    } else state.grades

    SettingsScaffold(
        title = "学科成绩",
        onBack = onBack,
        actions = if (isParent) {
            { TextButton(onClick = { onOpenForm(null) }) { Text("录成绩") } }
        } else null,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) {
        when {
            state.loading && state.grades.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingState() }
            else -> {
                if (state.isParent && state.members.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.selectedUserId == null,
                                onClick = { vm.selectUser(null) },
                                label = { Text("全部") },
                            )
                        }
                        items(state.members, key = { it.id }) { member ->
                            FilterChip(
                                selected = state.selectedUserId == member.id,
                                onClick = { vm.selectUser(member.id) },
                                label = { Text(member.displayName) },
                            )
                        }
                    }
                }

                if (visible.isEmpty()) {
                    EmptyState(
                        title = "还没有成绩",
                        description = if (isParent) "点右上角「录成绩」记录考试" else "等待家长录入成绩",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 汇总卡（内存聚合）
                    GradeSummaryCard(grades = visible)

                    // 按学科分组
                    visible.groupBy { it.subject }.forEach { (subject, subjectGrades) ->
                        SettingsSectionLabel(subjectGroupLabel(subject, subjectGrades))
                        SettingsCard {
                            subjectGrades.forEachIndexed { index, grade ->
                                SettingsRow(
                                    title = "${formatScore(grade.score)} / ${formatScore(grade.scoreFull)}",
                                    subtitle = buildString {
                                        append(grade.examName ?: "未命名考试")
                                        append(" · ").append(grade.examDate)
                                        if (state.selectedUserId == null) {
                                            grade.assigneeUsername?.let { append(" · ").append(it) }
                                        }
                                    },
                                    onClick = if (isParent) {
                                        { onOpenForm(grade.id) }
                                    } else null,
                                    showDivider = index < subjectGrades.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeSummaryCard(grades: List<GradeDto>) {
    val count = grades.size
    // 平均得分率：score/score_full 百分比
    val avgPct = if (count > 0) {
        grades.sumOf { it.score / it.scoreFull } / count * 100
    } else 0.0
    val latest = grades.firstOrNull() // 后端按 exam_date desc 排序
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "共 $count 条 · 平均得分率 ${"%.1f".format(avgPct)}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                latest?.let {
                    Text(
                        "最近：${it.subject} ${formatScore(it.score)} 分（${it.examDate}）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun subjectGroupLabel(subject: String, grades: List<GradeDto>): String {
    val avgPct = grades.sumOf { it.score / it.scoreFull } / grades.size * 100
    return "$subject · 平均 ${"%.1f".format(avgPct)}% · ${grades.size} 条"
}

private fun formatScore(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
