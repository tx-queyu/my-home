package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

/**
 * 学习时长统计（v0.17.0）。
 * 家长：成员 chips（我自己 + 各孩子，切换重新请求）；今日/本周/累计三卡 + 按教材分布 + 最近学习。
 */
@Composable
fun StudyStatsScreen(
    isParent: Boolean,
    onBack: () -> Unit,
    vm: StudyStatsViewModel = hiltViewModel(),
) {
    LaunchedEffect(isParent) { vm.init(isParent) }
    val state by vm.ui.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = "学习时长",
        onBack = onBack,
    ) {
        if (state.loading && state.stats == null) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        } else {
            if (state.isParent && state.members.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedUserId == null,
                            onClick = { vm.selectUser(null) },
                            label = { Text("我自己") },
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

            val stats = state.stats
            if (stats == null || (stats.totalSeconds == 0 && stats.byTextbook.isEmpty())) {
                EmptyState(
                    title = "还没有学习记录",
                    description = "完成一轮朗读/学习/测评后会自动记录",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // 三卡：今日/本周/累计
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TimeColumn("今日", stats.todaySeconds)
                        TimeColumn("本周", stats.weekSeconds)
                        TimeColumn("累计", stats.totalSeconds)
                    }
                }

                if (stats.byTextbook.isNotEmpty()) {
                    SettingsSectionLabel("按教材分布")
                    SettingsCard {
                        stats.byTextbook.forEachIndexed { index, t ->
                            SettingsRow(
                                title = "${t.subject} · ${t.textbook}",
                                subtitle = "${t.totalSeconds / 60} 分钟 · ${t.sessionCount} 次",
                                showDivider = index < stats.byTextbook.lastIndex,
                            )
                        }
                    }
                }

                if (state.recent.isNotEmpty()) {
                    SettingsSectionLabel("最近学习")
                    SettingsCard {
                        state.recent.take(10).forEachIndexed { index, s ->
                            SettingsRow(
                                title = "${sessionTypeLabel(s.sessionType)} · ${s.textbook}",
                                subtitle = "${s.durationSeconds / 60} 分钟 · ${s.sessionDate}",
                                showDivider = index < minOf(state.recent.size, 10) - 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(label: String, seconds: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${seconds / 60}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "分钟",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sessionTypeLabel(type: String): String = when (type) {
    "reading" -> "朗读"
    "learn" -> "学习"
    "quiz" -> "测评"
    else -> type
}
