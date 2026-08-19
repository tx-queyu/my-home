package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** 新建/编辑学科成绩（v0.17.0，家长）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeFormScreen(
    gradeId: String?,
    onBack: () -> Unit,
    vm: GradeFormViewModel = hiltViewModel(),
) {
    val editing = gradeId != null
    LaunchedEffect(gradeId) { vm.init(gradeId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it) } }

    var subject by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("") }
    var scoreFull by remember { mutableStateOf("100") }
    var examName by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf(LocalDate.now().format(DATE_FMT)) }
    var note by remember { mutableStateOf("") }
    var assigneeUserId by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }

    fun submit() {
        val scoreVal = score.toDoubleOrNull()
        val fullVal = scoreFull.toDoubleOrNull()
        when {
            scoreVal == null || fullVal == null ->
                scope.launch { snackbarHost.showSnackbar("请输入有效的分数和满分") }
            fullVal <= 0 ->
                scope.launch { snackbarHost.showSnackbar("满分必须大于 0") }
            scoreVal > fullVal ->
                scope.launch { snackbarHost.showSnackbar("分数不能超过满分") }
            else -> vm.save(
                id = gradeId,
                subject = subject.trim(),
                score = scoreVal,
                scoreFull = fullVal,
                examName = examName.trim().ifEmpty { null },
                examDate = examDate,
                note = note.trim().ifEmpty { null },
                assigneeUserId = assigneeUserId!!,
                onDone = onBack,
            )
        }
    }

    LaunchedEffect(state.existing) {
        val g = state.existing
        if (g != null && !initialized) {
            subject = g.subject
            score = formatScoreInput(g.score)
            scoreFull = formatScoreInput(g.scoreFull)
            examName = g.examName ?: ""
            examDate = g.examDate
            note = g.note ?: ""
            assigneeUserId = g.assigneeUserId
            initialized = true
        }
    }
    // 单人家庭默认选第一个孩子
    LaunchedEffect(state.children) {
        if (assigneeUserId == null && state.children.size == 1) {
            assigneeUserId = state.children.first().id
        }
    }

    var subjectMenuExpanded by remember { mutableStateOf(false) }
    var childMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val childLabel = state.children.firstOrNull { it.id == assigneeUserId }?.displayName
        ?: "请选择孩子"

    SettingsScaffold(
        title = if (editing) "编辑成绩" else "录入成绩",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) {
        SettingsSectionLabel("成绩信息")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = subjectMenuExpanded,
                    onExpandedChange = { subjectMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("学科") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = subjectMenuExpanded,
                        onDismissRequest = { subjectMenuExpanded = false },
                    ) {
                        state.subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = { subject = s; subjectMenuExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = examName,
                    onValueChange = { examName = it },
                    label = { Text("考试名称（可空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = score,
                        onValueChange = { score = it.filterNum() },
                        label = { Text("分数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = scoreFull,
                        onValueChange = { scoreFull = it.filterNum() },
                        label = { Text("满分") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("考试日期：$examDate") }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可空）") },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                )
            }
        }
        SettingsSectionLabel("归属孩子")
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
                        label = { Text("选择孩子") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = childMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = childMenuExpanded,
                        onDismissRequest = { childMenuExpanded = false },
                    ) {
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
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { submit() },
            enabled = subject.isNotBlank() && !state.saving && assigneeUserId != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(if (editing) "保存" else "录入") }
        if (editing) {
            OutlinedButton(
                onClick = { vm.delete(gradeId!!, onDone = onBack) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("删除成绩", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(examDate) }
                .getOrNull()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        examDate = Instant.ofEpochMilli(it)
                            .atZone(ZoneOffset.UTC).toLocalDate().format(DATE_FMT)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** 允许数字 + 小数点，过滤其他字符。 */
private fun String.filterNum(): String =
    filter { it.isDigit() || it == '.' }.let { s ->
        // 只保留第一个小数点
        val firstDot = s.indexOf('.')
        if (firstDot < 0) s
        else s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "")
    }

private fun formatScoreInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
