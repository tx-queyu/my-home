package com.myhome.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.BuildConfig
import com.myhome.admin.BrandGuide
import com.myhome.admin.DeviceBrand
import com.myhome.admin.MyDeviceAdminReceiver
import com.myhome.net.dto.DeviceDto
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel
import com.myhome.util.toLocalSeenText

@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    vm: DeviceDetailViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(deviceId) { vm.load(deviceId) }

    SettingsScaffold(
        title = "设备详情",
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.load(deviceId) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        },
    ) {
        if (state.loading) {
            LoadingState()
            return@SettingsScaffold
        }
        val device = state.device
        if (device == null) {
            SettingsCard {
                SettingsRow(
                    title = state.error ?: "设备不存在",
                    onClick = null,
                    showDivider = false,
                    titleColor = MaterialTheme.colorScheme.error,
                )
            }
            return@SettingsScaffold
        }

        SettingsSectionLabel("账号")
        SettingsCard {
            SettingsRow(
                title = "昵称",
                trailing = { TrailingText(device.displayName ?: "—") },
                showDivider = true,
            )
            SettingsRow(
                title = "用户名",
                trailing = { TrailingText(device.username ?: "—") },
                showDivider = true,
            )
            SettingsRow(
                title = "所属家庭",
                trailing = { TrailingText(device.familyName ?: "—") },
                showDivider = false,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("系统")
        SettingsCard {
            SettingsRow(
                title = "操作系统",
                trailing = { TrailingText(osLabel(device)) },
                showDivider = true,
            )
            SettingsRow(
                title = "系统版本",
                trailing = { TrailingText(device.osVersion ?: "—") },
                showDivider = true,
            )
            SettingsRow(
                title = "厂商",
                trailing = { TrailingText(device.manufacturer ?: "—") },
                showDivider = true,
            )
            SettingsRow(
                title = "型号",
                trailing = { TrailingText(device.model ?: "—") },
                showDivider = false,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("设备")
        SettingsCard {
            SettingsRow(
                title = "名称",
                trailing = { TrailingText(device.deviceName) },
                showDivider = true,
            )
            SettingsRow(
                title = "Device Owner",
                trailing = { TrailingText(if (device.isDeviceOwner) "是" else "否") },
                showDivider = true,
            )
            SettingsRow(
                title = "卸载锁",
                trailing = { TrailingText(if (device.isBlocked) "已开启" else "未开启") },
                showDivider = true,
            )
            SettingsRow(
                title = "最近在线",
                trailing = { TrailingText(device.lastSeen.toLocalSeenText()) },
                showDivider = false,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("远程控制")
        SettingsCard {
            SettingsRow(
                title = "阻止应用卸载",
                subtitle = when {
                    !state.isParent -> "仅家长或家庭管理员可操作"
                    !device.isDeviceOwner -> "需先在平板上执行 ADB 设置"
                    state.isWorking -> "正在下发指令…"
                    else -> "开启后平板无法卸载我家 App"
                },
                onClick = null,
                trailing = {
                    Switch(
                        checked = device.isBlocked,
                        enabled = state.isParent && device.isDeviceOwner && !state.isWorking,
                        onCheckedChange = { vm.toggle(deviceId, device.isBlocked) },
                    )
                },
                showDivider = false,
            )
        }

        if (state.isParent && !device.isDeviceOwner) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("首次设置（平板上执行，一次性）")
            if (device.osType == "android") {
                val brand = remember(device.manufacturer) {
                    BrandGuide.brandFamily(device.manufacturer)
                }
                SetupInstructionsCard(
                    brand = brand,
                    dpmCommand = remember {
                        "adb shell dpm set-device-owner " +
                            "${BuildConfig.APPLICATION_ID}/${MyDeviceAdminReceiver::class.java.name}"
                    },
                    onRefresh = { vm.load(deviceId) },
                    onOpenSetup = onOpenSetup,
                )
            } else {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "鸿蒙设备暂不支持远程激活 Device Owner",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "鸿蒙系统（HarmonyOS NEXT）不基于 Android，" +
                                "没有 ADB 协议和 Device Owner 概念，无法通过无线 ADB 远程开启卸载锁。" +
                                "可考虑使用鸿蒙自带的「应用锁」防误打开（但不能防卸载），" +
                                "或换用 Android 11+ 平板。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    title = state.error!!,
                    onClick = null,
                    showDivider = false,
                    titleColor = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TrailingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun osLabel(device: DeviceDto): String = when (device.osType) {
    "harmony" -> "鸿蒙"
    "android" -> "Android"
    else -> device.osType
}

@Composable
private fun SetupInstructionsCard(
    brand: DeviceBrand,
    dpmCommand: String,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = BrandGuide.cardTitle(brand),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val steps = BrandGuide.setupSteps(brand)
            steps.forEachIndexed { i, step ->
                Text(
                    text = "${i + 1}. $step",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onOpenSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("一键激活", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "（备用）复制下方命令到 LADB 等工具手动执行",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dpmCommand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(dpmCommand))
                    }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "复制命令",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("已执行，刷新状态")
            }
        }
    }
}
