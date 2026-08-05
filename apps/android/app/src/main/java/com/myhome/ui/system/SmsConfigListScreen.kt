package com.myhome.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.myhome.net.dto.SmsConfigDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsConfigListScreen(
    onBack: () -> Unit,
    onOpenEdit: (String?) -> Unit,
    vm: SmsConfigListViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<SmsConfigDto?>(null) }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); vm.consumeToast() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "短信配置",
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
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenEdit(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "新建")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.configs.isEmpty() -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                state.configs.isEmpty() -> EmptyState(
                    title = "还没有短信配置",
                    description = "点击右下角加号新建配置",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.configs, key = { it.id }) { cfg ->
                        SmsConfigCard(
                            cfg = cfg,
                            onActivate = { vm.activate(cfg.id) },
                            onDeactivate = { vm.deactivate(cfg.id) },
                            onTest = { vm.test(cfg.id) },
                            onEdit = { onOpenEdit(cfg.id) },
                            onDelete = { deleteTarget = cfg },
                            saving = state.saving,
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { cfg ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除配置") },
            text = { Text("确定删除 ${cfg.provider} 短信配置？") },
            confirmButton = {
                TextButton(onClick = {
                    val target = cfg
                    deleteTarget = null
                    vm.delete(target.id)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SmsConfigCard(
    cfg: SmsConfigDto,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    saving: Boolean,
) {
    SettingsCard {
        SettingsRow(
            title = smsProviderLabel(cfg.provider),
            subtitle = buildString {
                append(cfg.signName ?: "（未设置签名）")
                append(" · ").append(cfg.templateCode ?: "（未设置模板）")
                if (cfg.isActive) append(" · 已激活")
            },
            onClick = onEdit,
            showDivider = false,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillAction("测试", onTest, enabled = !saving)
            if (cfg.isActive) {
                PillAction("停用", onDeactivate, enabled = !saving)
            } else {
                PillAction("激活", onActivate, enabled = !saving, primary = true)
            }
            PillAction("删除", onDelete, enabled = !saving, destructive = true)
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

private fun smsProviderLabel(provider: String): String = when (provider) {
    "aliyun" -> "阿里云短信"
    "tencent" -> "腾讯云短信"
    "huawei" -> "华为云短信"
    else -> provider
}
