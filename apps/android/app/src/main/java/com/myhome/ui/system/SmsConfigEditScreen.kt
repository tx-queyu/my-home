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
fun SmsConfigEditScreen(
    configId: String?,
    onBack: () -> Unit,
    vm: SmsConfigEditViewModel = hiltViewModel(),
) {
    val editing = configId != null
    LaunchedEffect(configId) { vm.init(configId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it) } }

    val existing = state.existing
    var provider by remember { mutableStateOf("aliyun") }
    var signName by remember { mutableStateOf("") }
    var templateCode by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var accessKeySecret by remember { mutableStateOf("") }
    var sdkAppId by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var dailyLimit by remember { mutableStateOf("1000") }
    var intervalSeconds by remember { mutableStateOf("60") }
    var showSecret by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (editing && existing != null && !initialized) {
            provider = existing.provider
            signName = existing.signName ?: ""
            templateCode = existing.templateCode ?: ""
            sdkAppId = existing.sdkAppId ?: ""
            region = existing.region ?: ""
            dailyLimit = existing.dailyLimit.toString()
            intervalSeconds = existing.intervalSeconds.toString()
            initialized = true
        }
    }

    val isTencent = provider == "tencent"

    SettingsScaffold(
        title = if (editing) "编辑短信配置" else "新建短信配置",
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
                        value = smsProviderLabel(provider),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("短信服务商") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = providerMenuExpanded,
                        onDismissRequest = { providerMenuExpanded = false },
                    ) {
                        listOf("aliyun", "tencent", "huawei").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(smsProviderLabel(p)) },
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
        SettingsSectionLabel("签名与模板")
        SettingsCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = signName,
                    onValueChange = { signName = it },
                    label = { Text("短信签名 SignName") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = templateCode,
                    onValueChange = { templateCode = it },
                    label = { Text("模板编号 TemplateCode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isTencent) {
                    OutlinedTextField(
                        value = sdkAppId,
                        onValueChange = { sdkAppId = it },
                        label = { Text("腾讯云 SmsSdkAppId") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Region（可留空走默认）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
                    trailingIcon = {
                        Text(
                            text = if (showSecret) "隐藏" else "显示",
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    },
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
        val akFilled = accessKeyId.isNotBlank() && accessKeySecret.isNotBlank()
        val akKept = editing && existing?.accessKeyIdConfigured == true
        val valid = signName.isNotBlank() && templateCode.isNotBlank() && (akFilled || akKept) && (!isTencent || sdkAppId.isNotBlank())
        Button(
            onClick = {
                val dl = dailyLimit.toIntOrNull() ?: 1000
                val isec = intervalSeconds.toIntOrNull() ?: 60
                if (editing && configId != null) {
                    vm.update(
                        id = configId,
                        signName = signName,
                        templateCode = templateCode,
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret,
                        sdkAppId = sdkAppId,
                        region = region,
                        dailyLimit = dl,
                        intervalSeconds = isec,
                        onDone = onBack,
                    )
                } else {
                    vm.create(
                        provider = provider,
                        signName = signName,
                        templateCode = templateCode,
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret,
                        sdkAppId = sdkAppId,
                        region = region,
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

private fun smsProviderLabel(provider: String): String = when (provider) {
    "aliyun" -> "阿里云短信"
    "tencent" -> "腾讯云短信"
    "huawei" -> "华为云短信"
    else -> provider
}
