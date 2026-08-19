package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.CourseSessionType
import com.myhome.net.dto.SkillOverviewDto
import com.myhome.net.dto.TaskDto
import com.myhome.net.dto.sessionType
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildEducationScreen(
    onOpenTask: (String) -> Unit,
    onOpenPoints: () -> Unit,
    onOpenRewards: () -> Unit,
    onOpenSkillCenter: () -> Unit,
    onOpenHabits: () -> Unit,
    vm: ChildEducationViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "教育",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillAction(label = "积分", onClick = onOpenPoints)
                        PillAction(label = "奖励", onClick = onOpenRewards)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 首屏:我的能力大卡
                    state.mySkill?.let { skill ->
                        item {
                            MySkillCard(skill = skill, onClick = onOpenSkillCenter)
                        }
                    }
                    // 次屏:每日打卡卡
                    item {
                        HabitCheckinCard(
                            doneCount = state.habits.count { it.todayCheckedIn },
                            totalCount = state.habits.count { it.isActive },
                            onClick = onOpenHabits,
                        )
                    }
                    // 三屏:积分卡
                    item {
                        PointsBalanceCard(
                            balance = state.balance,
                            onOpenPoints = onOpenPoints,
                            onOpenRewards = onOpenRewards,
                        )
                    }
                    if (state.tasks.isEmpty()) {
                        item {
                            EmptyState(
                                title = "还没有任务",
                                description = "等待家长布置任务",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        items(state.tasks, key = { it.id }) { task ->
                            val completed = if (task.recurrenceType == "one_off") {
                                task.id in state.completedTaskIds
                            } else {
                                task.completedToday
                            }
                            ChildTaskCard(task, completed, onClick = { onOpenTask(task.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MySkillCard(skill: SkillOverviewDto, onClick: () -> Unit) {
    SettingsCard {
        Surface(
            onClick = onClick,
            color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "我的能力 · 英语单词",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "已掌握 ${skill.masteredWords} 词",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "接触过 ${skill.assessedWords} 词",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "熟悉 ${skill.byState["familiar"] ?: 0} · 学习中 ${skill.byState["learning"] ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "点击查看能力中心",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HabitCheckinCard(doneCount: Int, totalCount: Int, onClick: () -> Unit) {
    SettingsCard {
        SettingsRow(
            title = "每日打卡",
            subtitle = if (totalCount > 0) "今日 $doneCount/$totalCount" else "坚持养成好习惯",
            onClick = onClick,
            leading = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            showDivider = false,
        )
    }
}

@Composable
private fun PointsBalanceCard(
    balance: Int,
    onOpenPoints: () -> Unit,
    onOpenRewards: () -> Unit,
) {
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
                    "我的积分",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$balance",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillAction(label = "流水", onClick = onOpenPoints)
                PillAction(label = "兑换", onClick = onOpenRewards)
            }
        }
    }
}

@Composable
private fun ChildTaskCard(task: TaskDto, completed: Boolean, onClick: () -> Unit) {
    SettingsCard {
        SettingsRow(
            title = task.title,
            subtitle = buildString {
                task.course?.subject?.let { append("[$it] ") }
                when (task.course?.sessionType()) {
                    CourseSessionType.READING -> append("[需朗读] ")
                    CourseSessionType.LEARN -> append("[需学习] ")
                    CourseSessionType.QUIZ -> append("[需测评] ")
                    null -> {}
                }
                append("${task.points} 积分")
                task.assigneeUsername?.let { append(" · 指派 $it") }
                when (task.recurrenceType) {
                    "daily" -> append(" · 每天")
                    "weekly" -> append(" · 每周${formatWeekdays(task.recurrenceWeekdays)}")
                }
                if (task.availableStartDate != null || task.availableEndDate != null) {
                    append(" · ${task.availableStartDate ?: "…"}~${task.availableEndDate ?: "…"}")
                }
                if (task.availableStartTime != null && task.availableEndTime != null) {
                    append(" · ${task.availableStartTime.take(5)}-${task.availableEndTime.take(5)}")
                }
                task.dueDate?.let { append(" · 截止 $it") }
            },
            onClick = onClick,
            titleColor = if (completed) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            trailing = if (completed) {
                {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已完成",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else null,
            showDivider = false,
        )
    }
}

private val WEEKDAY_NAMES = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")

private fun formatWeekdays(weekdays: List<Int>?): String {
    if (weekdays.isNullOrEmpty()) return ""
    return weekdays.sorted().mapNotNull { WEEKDAY_NAMES[it] }.joinToString("")
}
