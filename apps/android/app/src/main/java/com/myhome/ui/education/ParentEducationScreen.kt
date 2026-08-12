package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.CourseSessionType
import com.myhome.net.dto.FamilyPointAccountDto
import com.myhome.net.dto.SelfStudyTextbookDto
import com.myhome.net.dto.TaskDto
import com.myhome.net.dto.sessionType
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentEducationScreen(
    onCreateTask: () -> Unit,
    onOpenRedemptions: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenRewards: () -> Unit,
    onOpenDeviceControl: () -> Unit,
    onOpenChildSkill: (String, String) -> Unit,
    onOpenSelfSkill: () -> Unit,
    onOpenSelfSession: (CourseSessionType, String) -> Unit,
    vm: ParentEducationViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()
    // v0.16.0:家长界面顶部 tab —— 教育(管孩子) / 自学(自己学,无积分)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddTextbook by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHostState.showSnackbar(it); vm.clearToast() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "学习",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    divider = {},
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "教育",
                                fontWeight = if (selectedTab == 0) FontWeight.SemiBold
                                else FontWeight.Normal,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "自学",
                                fontWeight = if (selectedTab == 1) FontWeight.SemiBold
                                else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = onCreateTask) {
                    Icon(Icons.Filled.Add, contentDescription = "新建任务")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                selectedTab == 1 -> SelfStudyContent(
                    selfTextbooks = state.selfTextbooks,
                    onOpenSelfSkill = onOpenSelfSkill,
                    onOpenSelfSession = onOpenSelfSession,
                    onAddTextbook = {
                        vm.loadAvailableTextbooks()
                        showAddTextbook = true
                    },
                )
                else -> EducationContent(
                    state = state,
                    onCreateTask = onCreateTask,
                    onOpenRedemptions = onOpenRedemptions,
                    onOpenTask = onOpenTask,
                    onOpenRewards = onOpenRewards,
                    onOpenDeviceControl = onOpenDeviceControl,
                    onOpenChildSkill = onOpenChildSkill,
                )
            }
        }
    }

    if (showAddTextbook) {
        val added = state.selfTextbooks.map { it.subject to it.textbook }.toSet()
        val options = state.availableTextbooks.orEmpty().filter { option ->
            option.courses.any { it.sessionType() != null } &&
                (option.subject to option.textbook) !in added
        }
        AlertDialog(
            onDismissRequest = { showAddTextbook = false },
            title = { Text("添加教材", fontWeight = FontWeight.SemiBold) },
            text = {
                if (state.availableTextbooks == null) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (options.isEmpty()) {
                    Text(
                        "暂无可添加的教材",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // v0.16.1:教材可能很多(KET/托业/雅思 + 20 册 PEP),用有界高度
                    // 的 LazyColumn 让列表可滚动,否则 Column 撑出屏外底部的教材点不到。
                    // 与 FamilyDetailScreen.AddMemberDialog 同一模式。
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(options, key = { it.subject to it.textbook }) { option ->
                            Surface(
                                onClick = {
                                    vm.addTextbook(option.subject, option.textbook)
                                    showAddTextbook = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${option.subject} · ${option.textbook}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = option.courses
                                            .mapNotNull { it.sessionType() }
                                            .distinct()
                                            .joinToString(" / ") {
                                                when (it) {
                                                    CourseSessionType.READING -> "朗读"
                                                    CourseSessionType.LEARN -> "学习"
                                                    CourseSessionType.QUIZ -> "测评"
                                                }
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTextbook = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EducationContent(
    state: ParentEducationUiState,
    onCreateTask: () -> Unit,
    onOpenRedemptions: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenRewards: () -> Unit,
    onOpenDeviceControl: () -> Unit,
    onOpenChildSkill: (String, String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCard {
                SettingsRow(
                    title = "奖励",
                    subtitle = "奖励与兑换管理",
                    onClick = onOpenRewards,
                    leading = {
                        Icon(
                            Icons.Filled.CardGiftcard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    showDivider = true,
                )
                SettingsRow(
                    title = "设备管控",
                    subtitle = "孩子设备使用管控",
                    onClick = onOpenDeviceControl,
                    leading = {
                        Icon(
                            Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    showDivider = false,
                )
            }
        }

        item { SettingsSectionLabel("孩子积分") }
        item { ChildrenPointsCard(state.childAccounts) }

        if (state.childSkills.isNotEmpty()) {
            item { SettingsSectionLabel("孩子能力 · 英语") }
            item {
                ChildSkillCard(
                    childSkills = state.childSkills,
                    onOpen = { childId, name -> onOpenChildSkill(childId, name) },
                )
            }
        }

        item { SettingsSectionLabel("兑换审批") }
        item {
            SettingsCard {
                val n = state.pendingRedemptionCount
                SettingsRow(
                    title = if (n > 0) "$n 个待处理" else "全部已处理",
                    subtitle = if (n > 0) "孩子用积分兑换的奖励待你发放"
                    else "暂无待审批的兑换",
                    onClick = onOpenRedemptions,
                    showDivider = false,
                )
            }
        }

        item { SettingsSectionLabel("今日完成 · ${state.todayRecords.size}") }
        item { TodayCompletionCard(state) }

        item { SettingsSectionLabel("全部任务 · ${state.tasks.size}") }
        if (state.tasks.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有任务",
                    description = "点击右下角加号添加任务",
                    actionLabel = "新建任务",
                    onAction = onCreateTask,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(state.tasks, key = { it.id }) { task ->
                ParentTaskCard(
                    task = task,
                    completerName = state.completerNameByTaskId[task.id],
                    isCompletedToday = task.id in state.todayCompletedTaskIds,
                    onClick = { onOpenTask(task.id) },
                )
            }
        }
    }
}

/** 自学 tab(v0.16.1):我的能力中心 + 我的教材(教材 → 朗读/学习/测评)。学习不产生积分。 */
@Composable
private fun SelfStudyContent(
    selfTextbooks: List<SelfStudyTextbookDto>,
    onOpenSelfSkill: () -> Unit,
    onOpenSelfSession: (CourseSessionType, String) -> Unit,
    onAddTextbook: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsSectionLabel("我的能力") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "我的能力中心",
                    subtitle = "查看我的单词掌握情况",
                    onClick = onOpenSelfSkill,
                    showDivider = false,
                )
            }
        }

        item { SettingsSectionLabel("我的教材") }
        if (selfTextbooks.isEmpty()) {
            item {
                SettingsCard {
                    SettingsRow(
                        title = "还没有教材",
                        subtitle = "添加一本教材开始学习",
                        onClick = null,
                        showDivider = false,
                    )
                }
            }
        } else {
            items(selfTextbooks, key = { it.id }) { textbook ->
                SelfTextbookCard(textbook, onOpenSelfSession)
            }
        }
        item {
            SettingsCard {
                SettingsRow(
                    title = "添加教材",
                    subtitle = "如 英语·KET",
                    onClick = onAddTextbook,
                    leading = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    showDivider = false,
                )
            }
        }

        item {
            Text(
                text = "自学不获得积分，学习进度会计入我的能力中心",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** 教材卡：点击展开/收起该教材下的互动课程（朗读/学习/测评）。 */
@Composable
private fun SelfTextbookCard(
    textbook: SelfStudyTextbookDto,
    onOpenSelfSession: (CourseSessionType, String) -> Unit,
) {
    var expanded by rememberSaveable(textbook.id) { mutableStateOf(false) }
    val sessionCourses = textbook.courses
        .mapNotNull { c -> c.sessionType()?.let { it to c } }
        .sortedBy { it.first.ordinal }

    SettingsCard {
        SettingsRow(
            title = "${textbook.subject} · ${textbook.textbook}",
            subtitle = "${sessionCourses.size} 门课程",
            onClick = { expanded = !expanded },
            trailing = {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            showDivider = expanded && sessionCourses.isNotEmpty(),
        )
        if (expanded) {
            sessionCourses.forEachIndexed { idx, (type, course) ->
                SettingsRow(
                    title = course.learningMethod,
                    subtitle = when (type) {
                        CourseSessionType.READING -> "跟读 + 拼读 + 发音评测"
                        CourseSessionType.LEARN -> "学新词 + 拼写检验 · 10 词一轮"
                        CourseSessionType.QUIZ -> "拼写测评 · 15 题"
                    },
                    onClick = { onOpenSelfSession(type, course.id) },
                    showDivider = idx < sessionCourses.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun ChildrenPointsCard(children: List<FamilyPointAccountDto>) {
    SettingsCard {
        if (children.isEmpty()) {
            SettingsRow(
                title = "还没有孩子账号",
                subtitle = "到「我的 → 家庭成员管理」添加",
                onClick = null,
                showDivider = false,
            )
        } else {
            children.forEachIndexed { idx, acc ->
                SettingsRow(
                    title = acc.displayName,
                    subtitle = "(${acc.username})",
                    onClick = null,
                    trailing = {
                        Text(
                            text = "${acc.balance}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    showDivider = idx < children.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun TodayCompletionCard(state: ParentEducationUiState) {
    SettingsCard {
        if (state.todayRecords.isEmpty()) {
            SettingsRow(
                title = "今天还没有任务被完成",
                onClick = null,
                showDivider = false,
            )
        } else {
            state.todayRecords.forEachIndexed { idx, rec ->
                val childName = state.accounts.firstOrNull { it.userId == rec.userId }?.displayName
                    ?: "孩子"
                val task = state.tasks.firstOrNull { it.id == rec.taskId }
                SettingsRow(
                    title = task?.title ?: "任务",
                    subtitle = "$childName · +${rec.pointsEarned} 积分",
                    onClick = null,
                    leading = {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    showDivider = idx < state.todayRecords.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun ParentTaskCard(
    task: TaskDto,
    completerName: String?,
    isCompletedToday: Boolean,
    onClick: () -> Unit,
) {
    SettingsCard {
        SettingsRow(
            title = task.title,
            subtitle = buildString {
                task.course?.subject?.let { append("[$it] ") }
                append("${task.points} 积分")
                task.assigneeUsername?.let { append(" · 指派 $it") }
            },
            onClick = onClick,
            trailing = {
                if (isCompletedToday && completerName != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = completerName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else {
                    Icon(
                        Icons.Filled.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            showDivider = false,
        )
    }
}

@Composable
private fun ChildSkillCard(
    childSkills: List<ChildSkillSummary>,
    onOpen: (String, String) -> Unit,
) {
    SettingsCard {
        childSkills.forEachIndexed { idx, summary ->
            val child = summary.child
            val overview = summary.overview
            val textbooks = summary.textbooks
            Column {
                SettingsRow(
                    title = child.displayName,
                    subtitle = when {
                        overview == null -> "暂无能力数据"
                        overview.assessedWords == 0 -> "还没开始评估"
                        else -> "已掌握 ${overview.masteredWords} 词 · 接触过 ${overview.assessedWords} 词"
                    },
                    onClick = { onOpen(child.userId, child.displayName) },
                    showDivider = false,
                )
                // 教材 chips:每个教材一个小 pill
                if (textbooks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        textbooks.take(3).forEach { textbook ->
                            TextbookPill(textbook)
                        }
                        if (textbooks.size > 3) {
                            Text(
                                text = "+${textbooks.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
                if (idx < childSkills.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp)
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextbookPill(textbook: com.myhome.net.dto.TextbookCoverageDto) {
    val isCompleted = textbook.isCompleted
    val label = if (isCompleted) {
        "🏆 ${textbook.textbook}"
    } else {
        "${textbook.textbook} ${(textbook.masteredCoverage * 100).toInt()}%"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isCompleted) {
            androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCompleted) {
                androidx.compose.ui.graphics.Color(0xFF10B981)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
