package com.myhome.ui.mine

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.BuildConfig
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenVersionInfo: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenLocalDeviceControl: () -> Unit,
    onOpenSwitchAccount: () -> Unit,
    onOpenFamilyMembers: () -> Unit,
    onOpenChangePassword: () -> Unit,
    vm: MineViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val roleLabel = com.myhome.util.RoleUtil.label(state.roles)
    SettingsScaffold(title = "设置", onBack = onBack) {
        SettingsSectionLabel("账号信息")
        SettingsCard {
            SettingsRow(
                title = "用户名",
                trailing = { TrailingText(state.username.ifBlank { "—" }) },
                showDivider = true,
            )
            SettingsRow(
                title = "角色",
                trailing = { TrailingText(roleLabel.ifBlank { "—" }) },
                showDivider = true,
            )
            SettingsRow(
                title = "家庭",
                trailing = { TrailingText(state.familyName.ifBlank { "—" }) },
                showDivider = true,
            )
            SettingsRow(
                title = "修改密码",
                onClick = onOpenChangePassword,
                leading = { LeadingIcon(Icons.Filled.Lock) },
                showDivider = false,
            )
        }
        if (state.roles.any { it == "parent" || it == "child" || it == "family_admin" }) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("家庭")
            SettingsCard {
                SettingsRow(
                    title = "家庭成员",
                    subtitle = if (com.myhome.util.RoleUtil.canManageFamily(state.roles)) "添加孩子、移除成员、授权管理员"
                    else "查看家庭成员",
                    onClick = onOpenFamilyMembers,
                    leading = { LeadingIcon(Icons.Filled.Group) },
                    showDivider = false,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("应用")
        SettingsCard {
            SettingsRow(
                title = "检查更新",
                onClick = onOpenUpdate,
                leading = { LeadingIcon(Icons.Filled.SystemUpdate) },
                trailing = { TrailingText("v${BuildConfig.VERSION_NAME}") },
                showDivider = true,
            )
            SettingsRow(
                title = "版本",
                onClick = onOpenVersionInfo,
                leading = { LeadingIcon(Icons.Filled.Info) },
                showDivider = true,
            )
            SettingsRow(
                title = "关于",
                onClick = onOpenAbout,
                leading = { LeadingIcon(Icons.Filled.Description) },
                showDivider = false,
            )
        }
        if (com.myhome.util.RoleUtil.canManageFamily(state.roles)) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("设备")
            SettingsCard {
                SettingsRow(
                    title = "设备管理",
                    subtitle = "远程控制家庭成员的平板",
                    onClick = onOpenDevices,
                    leading = { LeadingIcon(Icons.Filled.Devices) },
                    showDivider = true,
                )
                SettingsRow(
                    title = "本地设备管控",
                    subtitle = "管理当前这台设备",
                    onClick = onOpenLocalDeviceControl,
                    leading = { LeadingIcon(Icons.Filled.PhonelinkLock) },
                    showDivider = false,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsCard {
            SettingsRow(
                title = "切换账号",
                onClick = onOpenSwitchAccount,
                leading = { LeadingIcon(Icons.Filled.SwitchAccount) },
                showDivider = true,
            )
            SettingsRow(
                title = "退出登录",
                onClick = vm::logout,
                leading = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(22.dp),
                    )
                },
                titleColor = Color(0xFFE53935),
                showDivider = false,
            )
        }
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun TrailingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
