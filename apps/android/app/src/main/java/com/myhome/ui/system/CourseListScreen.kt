package com.myhome.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.CourseDto
import com.myhome.net.dto.CourseSessionType
import com.myhome.net.dto.sessionType
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenSession: (CourseSessionType, String) -> Unit = { _, _ -> },
    vm: CourseListViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf<String?>(null) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }
    val expandedKeys = remember { mutableStateListOf<String>() }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); vm.consumeToast() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it) }
    }

    val subjects = remember(state.courses) {
        state.courses.map { it.subject }.distinct().sorted()
    }
    val subjectCounts = remember(state.courses) {
        state.courses.groupingBy { it.subject }.eachCount()
    }

    val filtered = remember(state.courses, searchQuery, selectedSubject) {
        state.courses.filter { c ->
            (selectedSubject == null || c.subject == selectedSubject) &&
                (searchQuery.isBlank() ||
                    c.textbook.contains(searchQuery, ignoreCase = true) ||
                    c.learningMethod.contains(searchQuery, ignoreCase = true))
        }
    }
    val byTextbook = remember(filtered) {
        filtered.groupBy { it.subject to it.textbook }
            .toList()
            .sortedBy { (key, _) -> key.first + key.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "课程管理",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索教材或方式，如「一年级」「雅思」「测试」") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // 学科筛选 dropdown
            ExposedDropdownMenuBox(
                expanded = subjectMenuExpanded,
                onExpandedChange = { subjectMenuExpanded = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = selectedSubject?.let { "$it（${subjectCounts[it] ?: 0}）" }
                        ?: "全部（${state.courses.size}）",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("学科筛选") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectMenuExpanded)
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = subjectMenuExpanded,
                    onDismissRequest = { subjectMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("全部（${state.courses.size}）") },
                        onClick = { selectedSubject = null; subjectMenuExpanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        leadingIcon = if (selectedSubject == null) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                    )
                    subjects.forEach { subject ->
                        DropdownMenuItem(
                            text = { Text("$subject（${subjectCounts[subject] ?: 0}）") },
                            onClick = { selectedSubject = subject; subjectMenuExpanded = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            leadingIcon = if (selectedSubject == subject) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
            }
            // 课程列表（可折叠教材）
            when {
                state.loading -> LoadingState()
                state.error != null && state.courses.isEmpty() -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                byTextbook.isEmpty() -> EmptyState(
                    title = if (searchQuery.isNotBlank()) "无匹配课程" else "还没有课程",
                    description = if (searchQuery.isNotBlank()) "调整搜索词或切换学科筛选" else null,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    byTextbook.forEach { (key, courses) ->
                        val (subject, textbook) = key
                        val expandKey = "${subject}|${textbook}"
                        val expanded = expandKey in expandedKeys
                        item(key = expandKey) {
                            TextbookHeader(
                                subject = subject,
                                textbook = textbook,
                                courseCount = courses.size,
                                showSubject = selectedSubject == null,
                                expanded = expanded,
                                onClick = {
                                    if (expanded) {
                                        expandedKeys.remove(expandKey)
                                    } else {
                                        expandedKeys.add(expandKey)
                                    }
                                },
                            )
                        }
                        if (expanded) {
                            items(courses, key = { it.id }) { course ->
                                // 互动课程（朗读/学习/测评）进对应 session；其余走一键体验（后端建任务+加分）
                                val onExperience: () -> Unit = when (val type = course.sessionType()) {
                                    null -> ({ vm.experience(course) })
                                    else -> ({ onOpenSession(type, course.id) })
                                }
                                CourseCard(
                                    course = course,
                                    onActivate = { vm.activate(course.id) },
                                    onDeactivate = { vm.deactivate(course.id) },
                                    onExperience = onExperience,
                                    onOpenDetail = { onOpenDetail(course.id) },
                                    saving = state.saving,
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
private fun TextbookHeader(
    subject: String,
    textbook: String,
    courseCount: Int,
    showSubject: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = textbook,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (showSubject) "$subject · $courseCount 条" else "$courseCount 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: CourseDto,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onExperience: () -> Unit,
    onOpenDetail: () -> Unit,
    saving: Boolean,
) {
    SettingsCard {
        SettingsRow(
            title = course.learningMethod,
            subtitle = buildString {
                append("+${course.defaultPoints} 积分")
                if (!course.isActive) append(" · 已停用")
            },
            onClick = onOpenDetail,
            titleColor = if (course.isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            showDivider = false,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (course.isActive) {
                PillAction("停用", onDeactivate, enabled = !saving)
                PillAction("体验", onExperience, enabled = !saving, primary = true)
            } else {
                PillAction("激活", onActivate, enabled = !saving, primary = true)
            }
            PillAction("查看介绍", onOpenDetail, enabled = !saving)
        }
    }
}

@Composable
private fun PillAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
) {
    val color = when {
        destructive -> MaterialTheme.colorScheme.errorContainer
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.onErrorContainer
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color,
        enabled = enabled,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
