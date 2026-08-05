package com.myhome.ui.system

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.CourseSessionType
import com.myhome.net.dto.sessionType
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

@Composable
fun CourseDetailScreen(
    courseId: String,
    onBack: () -> Unit,
    onOpenSession: (CourseSessionType, String) -> Unit = { _, _ -> },
    vm: CourseDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(courseId) { vm.load(courseId) }
    val state by vm.ui.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = "课程介绍",
        onBack = onBack,
    ) {
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error!!, onRetry = vm::refresh)
            state.course == null -> EmptyState(title = "课程不存在", modifier = Modifier.fillMaxSize())
            else -> {
                val c = state.course!!
                val sessionType = c.sessionType()
                val canStartSession = sessionType != null && c.isActive
                if (canStartSession) {
                    val actionLabel = when (sessionType) {
                        CourseSessionType.READING -> "开始朗读"
                        CourseSessionType.LEARN -> "开始学习"
                        CourseSessionType.QUIZ -> "开始测评"
                        else -> "开始练习"
                    }
                    Button(
                        onClick = { onOpenSession(sessionType!!, c.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(actionLabel) }
                }
                SettingsSectionLabel("学科与教材")
                SettingsCard {
                    SettingsRow(
                        title = "学科",
                        trailing = { Text(c.subject) },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "教材",
                        trailing = { Text(c.textbook) },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "学习方式",
                        trailing = { Text(c.learningMethod) },
                        showDivider = true,
                    )
                }

                SettingsSectionLabel("积分与状态")
                SettingsCard {
                    SettingsRow(
                        title = "默认积分",
                        trailing = { Text("+${c.defaultPoints}") },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "排序",
                        trailing = { Text("${c.sortOrder}") },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "状态",
                        trailing = {
                            Text(
                                text = if (c.isActive) "启用" else "停用",
                                color = if (c.isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "描述",
                        trailing = {
                            Text(
                                text = c.description?.ifBlank { "—" } ?: "—",
                                color = if (c.description.isNullOrBlank())
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        showDivider = false,
                    )
                }

                SettingsSectionLabel("元数据")
                SettingsCard {
                    SettingsRow(
                        title = "课程 ID",
                        trailing = {
                            Text(
                                text = c.id,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "创建时间",
                        trailing = { Text(c.createdAt?.take(10) ?: "—") },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "更新时间",
                        trailing = { Text(c.updatedAt?.take(10) ?: "—") },
                        showDivider = false,
                    )
                }
            }
        }
    }
}
