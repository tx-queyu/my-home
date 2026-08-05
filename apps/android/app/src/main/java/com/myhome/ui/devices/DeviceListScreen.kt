package com.myhome.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.DeviceDto
import com.myhome.util.toLocalSeenText
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onBack: () -> Unit,
    onOpenDevice: (String) -> Unit,
    vm: DeviceListViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设备管理",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                state.devices.isEmpty() -> EmptyState(
                    title = "家庭中还没有设备",
                    description = "请在平板上登录我家 App 自动注册",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.devices, key = { it.id }) { device ->
                        DeviceRow(
                            device = device,
                            onClick = { onOpenDevice(device.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceDto, onClick: () -> Unit) {
    SettingsCard {
        SettingsRow(
            title = device.deviceName,
            subtitle = buildString {
                device.displayName?.let { append(it) }
                device.username?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it)
                }
                if (isEmpty()) append("（未知账号）")
                append(" · ")
                append(osLabel(device))
                append(" · DO: ")
                append(if (device.isDeviceOwner) "是" else "否")
                append(" · 卸载锁: ")
                append(if (device.isBlocked) "是" else "否")
                device.lastSeen?.let { append(" · 最近在线: ${it.toLocalSeenText(shortFormat = true)}") }
            },
            onClick = onClick,
            showDivider = false,
        )
    }
}

private fun osLabel(device: DeviceDto): String {
    val os = when (device.osType) {
        "harmony" -> "鸿蒙"
        "android" -> "Android"
        else -> device.osType
    }
    val model = device.model?.takeIf { it.isNotBlank() && it != device.deviceName }
    return if (model != null) "$os · $model" else os
}
