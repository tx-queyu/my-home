package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.myhome.net.dto.HabitDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

/**
 * 每日打卡（v0.17.0）。
 * 双视角一屏：孩子/家长都可打卡自己；家长额外可新建/编辑/删除习惯 + 看全家最近打卡。
 */
@Composable
fun HabitListScreen(
    isParent: Boolean,
    onBack: () -> Unit,
    onOpenForm: (String?) -> Unit,
    vm: HabitListViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbarHost.showSnackbar(it); vm.consumeToast() } }
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it) } }

    SettingsScaffold(
        title = "每日打卡",
        onBack = onBack,
        actions = if (isParent) {
            {
                TextButton(onClick = { onOpenForm(null) }) { Text("新建习惯") }
            }
        } else null,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) {
        when {
            state.loading && state.habits.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingState() }
            state.habits.isEmpty() -> EmptyState(
                title = "还没有习惯",
                description = if (isParent) "点右上角「新建习惯」开始" else "等待家长创建习惯",
                modifier = Modifier.fillMaxWidth(),
            )
            else -> {
                SettingsSectionLabel("今日打卡")
                SettingsCard {
                    state.habits.forEachIndexed { index, habit ->
                        HabitCheckinRow(
                            habit = habit,
                            checkingIn = state.checkingIn == habit.id,
                            isParent = isParent,
                            onCheckIn = { vm.checkIn(habit) },
                            onEdit = { onOpenForm(habit.id) },
                            showDivider = index < state.habits.lastIndex,
                        )
                    }
                }

                if (isParent && state.recentLogs.isNotEmpty()) {
                    SettingsSectionLabel("最近打卡")
                    SettingsCard {
                        state.recentLogs.forEachIndexed { index, log ->
                            SettingsRow(
                                title = "${log.username ?: "?"} · ${log.habitName ?: "?"}",
                                subtitle = "连续 ${log.streakCount} 天 · +${log.pointsEarned} 积分 · ${log.checkinDate ?: ""}",
                                showDivider = index < state.recentLogs.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitCheckinRow(
    habit: HabitDto,
    checkingIn: Boolean,
    isParent: Boolean,
    onCheckIn: () -> Unit,
    onEdit: () -> Unit,
    showDivider: Boolean,
) {
    SettingsRow(
        title = habit.name,
        subtitle = "连续 ${habit.currentStreak} 天 · 每次 +${habit.points} 积分（封顶 ${habit.streakCap} 天）",
        onClick = if (isParent) onEdit else null,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    !habit.isActive -> Text(
                        "已停用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    habit.todayCheckedIn -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "今日已打卡",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    checkingIn -> CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp,
                    )
                    else -> CheckinPill(onClick = onCheckIn)
                }
                if (isParent) {
                    IconButton(onClick = onEdit, modifier = Modifier.height(32.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        showDivider = showDivider,
    )
}

@Composable
private fun CheckinPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = "打卡",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
