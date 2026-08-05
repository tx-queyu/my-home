package com.myhome.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailConfigEditScreen(
    configId: String?,
    onBack: () -> Unit,
    vm: EmailConfigEditViewModel = hiltViewModel(),
) {
    val editing = configId != null
    LaunchedEffect(configId) { vm.init(configId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it) } }

    val existing = state.existing
    var provider by remember { mutableStateOf("smtp") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var encryption by remember { mutableStateOf("ssl") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var accessKeySecret by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var fromEmail by remember { mutableStateOf("") }
    var fromName by remember { mutableStateOf("") }
    var dailyLimit by remember { mutableStateOf("200") }
    var intervalSeconds by remember { mutableStateOf("60") }
    var showSecret by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var encryptionMenuExpanded by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (editing && existing != null && !initialized) {
            provider = existing.provider
            smtpHost = existing.smtpHost ?: ""
            smtpPort = existing.smtpPort?.toString() ?: "465"
            encryption = existing.encryption ?: "ssl"
            username = existing.username ?: ""
            region = existing.region ?: ""
            fromEmail = existing.fromEmail ?: ""
            fromName = existing.fromName ?: ""
            dailyLimit = existing.dailyLimit.toString()
            intervalSeconds = existing.intervalSeconds.toString()
            initialized = true
        }
    }

    val isSmtp = provider == "smtp"

    SettingsScaffold(
        title = if (editing) "编辑邮箱配置" else "新建邮箱配置",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) {
        SettingsSectionLabel("服务商")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = providerMenuExpanded,
                    onExpandedChange = { providerMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = emailProviderLabel(provider),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("邮件服务商") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = providerMenuExpanded,
                        onDismissRequest = { providerMenuExpanded = false },
                    ) {
                        listOf("smtp", "aliyun", "tencent", "huawei").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(emailProviderLabel(p)) },
                                onClick = {
                                    provider = p
                                    providerMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        SettingsSectionLabel("发件人")
        SettingsCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = fromEmail,
                    onValueChange = { fromEmail = it },
                    label = { Text("发件邮箱 FromEmail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fromName,
                    onValueChange = { fromName = it },
                    label = { Text("发件人名称 FromName（可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (isSmtp) {
            SettingsSectionLabel("SMTP 服务器")
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = smtpHost,
                        onValueChange = { smtpHost = it },
                        label = { Text("SMTP 主机（如 smtp.qq.com）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = smtpPort,
                        onValueChange = { smtpPort = it.filter { c -> c.isDigit() } },
                        label = { Text("端口（SSL=465 / STARTTLS=587）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(
                        expanded = encryptionMenuExpanded,
                        onExpandedChange = { encryptionMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = encryptionLabel(encryption),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("加密方式") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = encryptionMenuExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = encryptionMenuExpanded,
                            onDismissRequest = { encryptionMenuExpanded = false },
                        ) {
                            listOf("ssl" to "SSL/TLS（465）", "starttls" to "STARTTLS（587）", "none" to "无加密（不推荐）").forEach { (v, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        encryption = v
                                        encryptionMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(if (editing && existing?.passwordConfigured == true) "用户名（留空保留原值）" else "用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (editing && existing?.passwordConfigured == true) "密码（留空保留原值）" else "密码") },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("显示密码")
                        Switch(checked = showSecret, onCheckedChange = { showSecret = it })
                    }
                }
            }
        } else {
            SettingsSectionLabel("AccessKey")
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = accessKeyId,
                        onValueChange = { accessKeyId = it },
                        label = { Text(if (editing && existing?.accessKeyIdConfigured == true) "AccessKeyId（留空保留原值）" else "AccessKeyId") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = accessKeySecret,
                        onValueChange = { accessKeySecret = it },
                        label = { Text(if (editing && existing?.accessKeyIdConfigured == true) "AccessKeySecret（留空保留原值）" else "AccessKeySecret") },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("显示密钥")
                        Switch(checked = showSecret, onCheckedChange = { showSecret = it })
                    }
                }
            }
            SettingsSectionLabel("区域")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text("Region（如 cn-hangzhou / ap-hongkong）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        SettingsSectionLabel("风控")
        SettingsCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = dailyLimit,
                    onValueChange = { dailyLimit = it.filter { c -> c.isDigit() } },
                    label = { Text("每日限额") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = intervalSeconds,
                    onValueChange = { intervalSeconds = it.filter { c -> c.isDigit() } },
                    label = { Text("发送间隔（秒）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val valid = fromEmail.isNotBlank() && (
            if (isSmtp) {
                smtpHost.isNotBlank() && username.isNotBlank() && (
                    password.isNotBlank() || (editing && existing?.passwordConfigured == true)
                )
            } else {
                (accessKeyId.isNotBlank() && accessKeySecret.isNotBlank()) ||
                    (editing && existing?.accessKeyIdConfigured == true)
            }
        )
        Button(
            onClick = {
                val dl = dailyLimit.toIntOrNull() ?: 200
                val isec = intervalSeconds.toIntOrNull() ?: 60
                val port = smtpPort.toIntOrNull()
                if (editing && configId != null) {
                    vm.update(
                        id = configId,
                        smtpHost = smtpHost,
                        smtpPort = port,
                        encryption = encryption,
                        username = username,
                        password = password,
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret,
                        region = region,
                        fromEmail = fromEmail,
                        fromName = fromName,
                        dailyLimit = dl,
                        intervalSeconds = isec,
                        onDone = onBack,
                    )
                } else {
                    vm.create(
                        provider = provider,
                        smtpHost = smtpHost,
                        smtpPort = port,
                        encryption = encryption,
                        username = username,
                        password = password,
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret,
                        region = region,
                        fromEmail = fromEmail,
                        fromName = fromName,
                        dailyLimit = dl,
                        intervalSeconds = isec,
                        onDone = onBack,
                    )
                }
            },
            enabled = valid && !state.saving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(if (editing) "保存" else "创建") }
    }
}

private fun encryptionLabel(enc: String): String = when (enc) {
    "ssl" -> "SSL/TLS（465）"
    "starttls" -> "STARTTLS（587）"
    "none" -> "无加密（不推荐）"
    else -> enc
}
