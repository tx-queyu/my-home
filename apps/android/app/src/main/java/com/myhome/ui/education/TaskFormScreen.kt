package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.myhome.net.dto.CourseDto
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsSectionLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun String?.toDisplayTime(): String = this?.takeIf { it.length >= 5 }?.substring(0, 5) ?: ""

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormScreen(
    taskId: String?,
    onBack: () -> Unit,
    vm: TaskFormViewModel = hiltViewModel(),
) {
    val editing = taskId != null
    LaunchedEffect(taskId) { vm.init(taskId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var courseId by remember { mutableStateOf<String?>(null) }
    var points by remember { mutableStateOf("1") }
    var dueDate by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }
    var assigneeUserId by remember { mutableStateOf<String?>(null) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var startTime by remember { mutableStateOf("") }   // "HH:MM" 或空
    var endTime by remember { mutableStateOf("") }
    var recurrenceType by remember { mutableStateOf("one_off") }
    var weekdays by remember { mutableStateOf(setOf<Int>()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(state.task) {
        if (editing && state.task != null && !initialized) {
            state.task!!.let {
                title = it.title
                description = it.description ?: ""
                courseId = it.courseId
                points = it.points.toString()
                dueDate = it.dueDate ?: ""
                isActive = it.isActive
                assigneeUserId = it.assigneeUserId
                startDate = it.availableStartDate?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() }
                endDate = it.availableEndDate?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() }
                startTime = it.availableStartTime.toDisplayTime()
                endTime = it.availableEndTime.toDisplayTime()
                recurrenceType = it.recurrenceType
                weekdays = it.recurrenceWeekdays?.toSet() ?: emptySet()
            }
            initialized = true
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it) }
    }

    var courseMenuExpanded by remember { mutableStateOf(false) }
    var childMenuExpanded by remember { mutableStateOf(false) }
    var recurrenceMenuExpanded by remember { mutableStateOf(false) }
    val selectedCourse: CourseDto? = state.courses.firstOrNull { it.id == courseId }
    val courseLabel = selectedCourse?.let { "${it.subject} · ${it.textbook} · ${it.learningMethod}" } ?: "（不选课程）"
    val childLabel = state.children.firstOrNull { it.id == assigneeUserId }?.displayName ?: "不指定（任意孩子）"
    val recurrenceLabel = when (recurrenceType) {
        "daily" -> "每天"
        "weekly" -> "每周"
        else -> "一次性"
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editing) "编辑任务" else "新建任务",
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
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionLabel("课程")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = courseMenuExpanded,
                        onExpandedChange = { courseMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = courseLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("选择课程") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseMenuExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = courseMenuExpanded,
                            onDismissRequest = { courseMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("（不选课程）") },
                                onClick = {
                                    courseId = null
                                    courseMenuExpanded = false
                                },
                            )
                            state.courses.forEach { course ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${course.subject} · ${course.textbook} · ${course.learningMethod} (+${course.defaultPoints})")
                                    },
                                    onClick = {
                                        courseId = course.id
                                        if (title.isBlank()) title = "${course.textbook} · ${course.learningMethod}"
                                        if (points == "1") points = course.defaultPoints.toString()
                                        if (description.isBlank() && !course.description.isNullOrBlank()) {
                                            description = course.description!!
                                        }
                                        courseMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            SettingsSectionLabel("基本信息")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }
            }
            SettingsSectionLabel("指派")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = childMenuExpanded,
                        onExpandedChange = { childMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = childLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("指派给孩子") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = childMenuExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = childMenuExpanded,
                            onDismissRequest = { childMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("不指定（任意孩子）") },
                                onClick = {
                                    assigneeUserId = null
                                    childMenuExpanded = false
                                },
                            )
                            state.children.forEach { child ->
                                DropdownMenuItem(
                                    text = { Text(child.displayName) },
                                    onClick = {
                                        assigneeUserId = child.id
                                        childMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            SettingsSectionLabel("可完成时间")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(startDate?.format(DATE_FMT) ?: "开始日期（不限）") }
                        OutlinedButton(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(endDate?.format(DATE_FMT) ?: "结束日期（不限）") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (startTime.isNotBlank()) "开始 $startTime" else "开始时间（全天）") }
                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (endTime.isNotBlank()) "结束 $endTime" else "结束时间（全天）") }
                    }
                    if (startDate != null || endDate != null || startTime.isNotBlank() || endTime.isNotBlank()) {
                        TextButton(
                            onClick = {
                                startDate = null; endDate = null
                                startTime = ""; endTime = ""
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("清除时间限制") }
                    }
                }
            }
            SettingsSectionLabel("重复")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = recurrenceMenuExpanded,
                        onExpandedChange = { recurrenceMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = recurrenceLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("重复方式") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceMenuExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = recurrenceMenuExpanded,
                            onDismissRequest = { recurrenceMenuExpanded = false },
                        ) {
                            listOf("one_off" to "一次性", "daily" to "每天", "weekly" to "每周").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        recurrenceType = value
                                        recurrenceMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (recurrenceType == "weekly") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            WEEKDAY_LABELS.forEachIndexed { index, label ->
                                val day = index + 1
                                FilterChip(
                                    selected = day in weekdays,
                                    onClick = {
                                        weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }
            SettingsSectionLabel("积分与截止")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = points,
                        onValueChange = { points = it.filter { c -> c.isDigit() } },
                        label = { Text("积分") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("截止日期（YYYY-MM-DD，可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("启用")
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val pointsInt = points.toIntOrNull() ?: 1
                    vm.save(
                        id = taskId,
                        title = title.trim(),
                        description = description.trim().ifEmpty { null },
                        courseId = courseId,
                        points = pointsInt,
                        dueDate = dueDate.trim().ifEmpty { null },
                        isActive = isActive,
                        assigneeUserId = assigneeUserId,
                        availableStartDate = startDate?.format(DATE_FMT),
                        availableEndDate = endDate?.format(DATE_FMT),
                        availableStartTime = startTime.ifBlank { null },
                        availableEndTime = endTime.ifBlank { null },
                        recurrenceType = recurrenceType,
                        recurrenceWeekdays = weekdays.sorted().ifEmpty { null },
                        onDone = onBack,
                    )
                },
                enabled = title.isNotBlank() && !state.saving &&
                    (recurrenceType != "weekly" || weekdays.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (editing) "保存" else "创建") }
        }
    }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showStartDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }
    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showEndDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }
    if (showStartTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = startTime.take(2).toIntOrNull() ?: 16,
            initialMinute = startTime.drop(3).take(2).toIntOrNull() ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startTime = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                    showStartTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
    if (showEndTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = endTime.take(2).toIntOrNull() ?: 21,
            initialMinute = endTime.drop(3).take(2).toIntOrNull() ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endTime = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                    showEndTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}
