package com.myhome.ui.education

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.ChildWordMasteryDto
import com.myhome.net.dto.SkillOverviewDto
import com.myhome.net.dto.TextbookCoverageDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsSectionLabel

private val STATE_LABEL = mapOf(
    "new" to "未评估",
    "learning" to "学习中",
    "familiar" to "熟悉",
    "mastered" to "已掌握",
)

private val STATE_COLOR = mapOf(
    "new" to 0xFF9CA3AF,
    "learning" to 0xFFF59E0B,
    "familiar" to 0xFF3B82F6,
    "mastered" to 0xFF10B981,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillCenterScreen(
    mode: SkillCenterMode,
    onBack: () -> Unit,
    vm: SkillCenterViewModel = hiltViewModel(),
) {
    LaunchedEffect(mode) { vm.load(mode) }
    val state by vm.ui.collectAsStateWithLifecycle()

    val title = when (mode) {
        is SkillCenterMode.Self -> "能力中心"
        is SkillCenterMode.Child -> "${mode.childName} 的能力"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.load(mode) })
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { OverviewCard(state.overview) }

                    item { SettingsSectionLabel("教材进度") }
                    if (state.textbooks.isEmpty()) {
                        item {
                            when (mode) {
                                is SkillCenterMode.Self -> EmptyState(
                                    title = "还没有教材",
                                    description = "在「学习 → 自学」添加教材后，这里会显示教材进度与单词明细",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                is SkillCenterMode.Child -> EmptyState(
                                    title = "还没有涉及课程的任务",
                                    description = "家长布置带课程的任务后，这里会显示教材进度与单词明细",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        item {
                            TextbookTabRow(
                                textbooks = state.textbooks,
                                selectedKey = state.selectedTextbookKey,
                                onSelect = { vm.selectTextbook(it) },
                            )
                        }
                        state.selectedTextbook?.let { textbook ->
                            item { TextbookProgressCard(textbook) }

                            item { SettingsSectionLabel("单词明细 · ${textbook.textbook}") }
                            item {
                                StateFilterRow(
                                    selected = state.selectedFilter,
                                    onSelect = { vm.setFilter(it) },
                                )
                            }
                            when {
                                state.wordsLoading -> item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                state.wordsError != null -> item {
                                    ErrorState(state.wordsError!!, onRetry = { vm.retryWords() })
                                }
                                state.filteredWords.isEmpty() -> item {
                                    EmptyState(
                                        title = "该状态下没有单词",
                                        description = "先做几次朗读任务以积累能力数据",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                else -> items(state.filteredWords, key = { it.lexemeId }) { word ->
                                    WordMasteryCard(word)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(overview: SkillOverviewDto?) {
    if (overview == null) return
    val mastered = overview.byState["mastered"] ?: 0
    val familiar = overview.byState["familiar"] ?: 0
    val learning = overview.byState["learning"] ?: 0
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "英语 · 单词",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "已掌握 ${overview.masteredWords} 词",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "接触过 ${overview.assessedWords} 词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "平均掌握度 ${(overview.averageMastery * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 接触词构成条:已掌握(绿) + 熟悉(蓝) + 学习中(橙)
            if (overview.assessedWords > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                ) {
                    if (mastered > 0) {
                        Box(
                            modifier = Modifier
                                .weight(mastered.toFloat())
                                .fillMaxHeight()
                                .background(Color(STATE_COLOR["mastered"]!!)),
                        )
                    }
                    if (familiar > 0) {
                        Box(
                            modifier = Modifier
                                .weight(familiar.toFloat())
                                .fillMaxHeight()
                                .background(Color(STATE_COLOR["familiar"]!!)),
                        )
                    }
                    if (learning > 0) {
                        Box(
                            modifier = Modifier
                                .weight(learning.toFloat())
                                .fillMaxHeight()
                                .background(Color(STATE_COLOR["learning"]!!)),
                        )
                    }
                }
            } else {
                Text(
                    text = "还没有接触过单词，先做一次朗读任务吧",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 状态 3 卡(仅接触过的词)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StateCountChip(
                    label = "已掌握",
                    count = mastered,
                    color = Color(STATE_COLOR["mastered"]!!),
                    modifier = Modifier.weight(1f),
                )
                StateCountChip(
                    label = "熟悉",
                    count = familiar,
                    color = Color(STATE_COLOR["familiar"]!!),
                    modifier = Modifier.weight(1f),
                )
                StateCountChip(
                    label = "学习中",
                    count = learning,
                    color = Color(STATE_COLOR["learning"]!!),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StateCountChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextbookTabRow(
    textbooks: List<TextbookCoverageDto>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    val selectedIndex = textbooks.indexOfFirst { it.key == selectedKey }
        .coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {},
    ) {
        textbooks.forEach { textbook ->
            val selected = textbook.key == selectedKey
            Tab(
                selected = selected,
                onClick = { onSelect(textbook.key) },
                text = {
                    Text(
                        text = buildString {
                            if (textbook.isCompleted) append("🏆 ")
                            append(textbook.textbook)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun TextbookProgressCard(textbook: TextbookCoverageDto) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${textbook.subject} · ${textbook.textbook}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (textbook.learningMethods.isNotEmpty()) {
                        Text(
                            text = textbook.learningMethods.joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "共 ${textbook.totalWords} 词 · 已掌握 ${textbook.masteredWords} · 接触过 ${textbook.touchedWords}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (textbook.isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                Icons.Filled.EmojiEvents,
                                contentDescription = "已通关",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.height(14.dp),
                            )
                            Text(
                                text = "已通关",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${(textbook.masteredCoverage * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // 双轨进度条:掌握(蓝) + 接触(灰底)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            ) {
                LinearProgressIndicator(
                    progress = { textbook.touchedCoverage.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                LinearProgressIndicator(
                    progress = { textbook.masteredCoverage.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    trackColor = Color.Transparent,
                    color = if (textbook.isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                )
            }
            if (!textbook.isCompleted && textbook.totalWords > textbook.masteredWords) {
                Text(
                    text = "再掌握 ${textbook.totalWords - textbook.masteredWords} 词即可通关 🏆",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StateFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部") },
            colors = FilterChipDefaults.filterChipColors(),
        )
        listOf("mastered", "familiar", "learning", "new").forEach { s ->
            FilterChip(
                selected = selected == s,
                onClick = { onSelect(s) },
                label = { Text(STATE_LABEL[s] ?: s) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun WordMasteryCard(word: ChildWordMasteryDto) {
    SettingsCard {
        SettingsRow(
            title = word.spelling,
            subtitle = buildString {
                word.phonetic?.let { append(it).append("  ") }
                word.meaningCn?.let { append(it) }
            },
            onClick = null,
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(STATE_COLOR[word.state] ?: 0xFF9CA3AF).copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = STATE_LABEL[word.state] ?: word.state,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(STATE_COLOR[word.state] ?: 0xFF9CA3AF),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (word.state != "new") {
                        Text(
                            text = "${(word.mastery * 100).toInt()}% · 练 ${word.attempts} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            showDivider = false,
        )
    }
}
