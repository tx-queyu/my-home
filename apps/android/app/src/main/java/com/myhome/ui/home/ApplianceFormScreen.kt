package com.myhome.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsSectionLabel

private val STATUS_OPTIONS = listOf("normal" to "正常", "broken" to "故障", "in_repair" to "维修中", "retired" to "已停用")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplianceFormScreen(
    applianceId: String?,
    onBack: () -> Unit,
    vm: ApplianceViewModel = hiltViewModel(),
) {
    val editing = applianceId != null
    LaunchedEffect(applianceId) {
        if (editing) vm.loadDetail(applianceId!!) else vm.clearDetail()
    }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val listState by vm.list.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("normal") }
    var notes by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(detail) {
        if (editing && detail != null && !initialized) {
            detail!!.let {
                name = it.name; type = it.type; location = it.location
                status = it.status; notes = it.notes ?: ""
            }
            initialized = true
        }
    }
    LaunchedEffect(listState.error) {
        listState.error?.let { snackbarHost.showSnackbar(it) }
    }

    var statusMenuExpanded by remember { mutableStateOf(false) }
    val statusLabel = STATUS_OPTIONS.firstOrNull { it.first == status }?.second ?: status

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editing) "编辑电器" else "新建电器",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionLabel("基本信息")
            SettingsCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称（如 客厅电视）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("类型（如 电视）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("位置（如 客厅）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SettingsSectionLabel("状态与备注")
            SettingsCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExposedDropdownMenuBox(
                        expanded = statusMenuExpanded,
                        onExpandedChange = { statusMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = statusLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("状态") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = statusMenuExpanded,
                            onDismissRequest = { statusMenuExpanded = false },
                        ) {
                            STATUS_OPTIONS.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        status = code
                                        statusMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val trimmedNotes = notes.trim().ifEmpty { null }
                    vm.save(
                        id = applianceId,
                        name = name.trim(),
                        type = type.trim(),
                        location = location.trim(),
                        status = status,
                        notes = trimmedNotes,
                        onDone = onBack,
                    )
                },
                enabled = name.isNotBlank() && type.isNotBlank() && location.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (editing) "保存" else "创建") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
