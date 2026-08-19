package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

/** 新建/编辑习惯（v0.17.0，家长）。 */
@Composable
fun HabitFormScreen(
    habitId: String?,
    onBack: () -> Unit,
    vm: HabitFormViewModel = hiltViewModel(),
) {
    val editing = habitId != null
    LaunchedEffect(habitId) { vm.init(habitId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it) } }

    var name by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("1") }
    var streakCap by remember { mutableStateOf("7") }
    var isActive by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(state.existing) {
        val h = state.existing
        if (h != null && !initialized) {
            name = h.name
            points = h.points.toString()
            streakCap = h.streakCap.toString()
            isActive = h.isActive
            initialized = true
        }
    }

    SettingsScaffold(
        title = if (editing) "编辑习惯" else "新建习惯",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) {
        SettingsSectionLabel("习惯信息")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("习惯名称（如：早起、阅读）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = points,
                    onValueChange = { points = it.filter { c -> c.isDigit() } },
                    label = { Text("基础积分（每次打卡最少）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = streakCap,
                    onValueChange = { streakCap = it.filter { c -> c.isDigit() } },
                    label = { Text("连续天数封顶（防攒分）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "当天积分 = 连续天数（最多 ${streakCap.toIntOrNull() ?: 7}）× ${points.toIntOrNull() ?: 1} 积分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                vm.save(
                    id = habitId,
                    name = name.trim(),
                    points = points.toIntOrNull() ?: 1,
                    streakCap = streakCap.toIntOrNull() ?: 7,
                    isActive = isActive,
                    onDone = onBack,
                )
            },
            enabled = name.isNotBlank() && !state.saving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(if (editing) "保存" else "创建") }
        if (editing) {
            OutlinedButton(
                onClick = { vm.delete(habitId!!, onDone = onBack) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("删除习惯", color = MaterialTheme.colorScheme.error) }
        }
    }
}
