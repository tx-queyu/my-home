package com.myhome.ui.admin

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.BuildConfig
import com.myhome.admin.BrandGuide
import com.myhome.admin.DeviceBrand
import com.myhome.admin.MyDeviceAdminReceiver
import com.myhome.admin.OsDetector
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

@Composable
fun DeviceControlScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    vm: DeviceControlViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScaffold(
        title = "设备管控",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        },
    ) {
        if (state.loading) {
            LoadingState()
            return@SettingsScaffold
        }

        SettingsSectionLabel("应用")
        SettingsCard {
            SettingsRow(
                title = "阻止应用卸载",
                subtitle = "开启后 Settings 中无法卸载我家 App（adb 仍可绕过，仅防小孩误操作）",
                onClick = null,
                trailing = {
                    Switch(
                        checked = state.isDeviceOwner && state.isBlocked,
                        enabled = state.isParent && state.isDeviceOwner && !state.isWorking,
                        onCheckedChange = { vm.toggle() },
                    )
                },
                showDivider = false,
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("设备状态")
        SettingsCard {
            SettingsRow(
                title = "Device Owner",
                onClick = null,
                leading = { StatusIcon(state.isDeviceOwner) },
                trailing = { StatusText(state.isDeviceOwner) },
                showDivider = true,
            )
            SettingsRow(
                title = "卸载已锁定",
                onClick = null,
                leading = { StatusIcon(state.isDeviceOwner && state.isBlocked) },
                trailing = { StatusText(state.isDeviceOwner && state.isBlocked) },
                showDivider = false,
            )
        }

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    title = state.error!!,
                    onClick = null,
                    titleColor = MaterialTheme.colorScheme.error,
                    showDivider = false,
                )
            }
        }

        if (!state.isParent) {
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    title = "仅家长账号可操作",
                    subtitle = "切换至家长账号后可使用此功能",
                    onClick = null,
                    showDivider = false,
                )
            }
        } else if (!state.isDeviceOwner) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("首次设置（无线 ADB）")
            if (com.myhome.admin.OsDetector.isHarmonyOs()) {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "鸿蒙设备暂不支持 Device Owner",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "鸿蒙系统（HarmonyOS NEXT）不基于 Android，" +
                                "没有 ADB 协议和 Device Owner 概念，无法开启卸载锁。" +
                                "可考虑使用鸿蒙自带的「应用锁」防误打开（但不能防卸载），" +
                                "或换用 Android 11+ 设备。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val brand = remember { BrandGuide.brandFamily(OsDetector.manufacturer()) }
                SetupInstructionsCard(
                    brand = brand,
                    dpmCommand = remember {
                        "adb shell dpm set-device-owner " +
                            "${BuildConfig.APPLICATION_ID}/${MyDeviceAdminReceiver::class.java.name}"
                    },
                    onRefresh = vm::refresh,
                    onOpenSetup = onOpenSetup,
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("危险操作")
            SettingsCard {
                SettingsRow(
                    title = "移除 Device Owner",
                    subtitle = "移除后需重新执行 ADB 设置才能再次开启（工厂重置 + 无线 ADB）",
                    onClick = vm::showRemoveConfirm,
                    titleColor = MaterialTheme.colorScheme.error,
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (state.showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissRemoveConfirm,
            title = { Text("移除 Device Owner") },
            text = {
                Text(
                    "移除后 App 将失去 Device Owner 权限，卸载锁定立即失效。" +
                        "要再次开启需工厂重置平板 + 重新执行无线 ADB 设置，确认继续？",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::removeDeviceOwner,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("确认移除") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissRemoveConfirm) { Text("取消") }
            },
        )
    }
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
                text = "（备用）传统手动方式：复制下方命令到 LADB 等工具执行",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
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
                Text("我已执行，检查状态")
            }
        }
    }
}

@Composable
private fun StatusIcon(ok: Boolean) {
    val icon: ImageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel
    val tint = if (ok) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun StatusText(ok: Boolean) {
    Text(
        text = if (ok) "是" else "否",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
