package com.myhome.ui.system

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

@Composable
fun SystemScreen(
    onOpenUsers: () -> Unit,
    onOpenFamilies: () -> Unit,
    onOpenRoles: () -> Unit,
    onOpenSmsConfigs: () -> Unit,
    onOpenEmailConfigs: () -> Unit,
    onOpenCourses: () -> Unit,
) {
    SettingsScaffold(title = "系统管理") {
        SettingsSectionLabel("管理")
        SettingsCard {
            SettingsRow(
                title = "用户管理",
                subtitle = "查看与编辑系统用户、角色、家庭归属",
                onClick = onOpenUsers,
                leading = { LeadingIcon(Icons.Filled.Group) },
                showDivider = true,
            )
            SettingsRow(
                title = "家庭管理",
                subtitle = "查看系统内所有家庭及成员详情",
                onClick = onOpenFamilies,
                leading = { LeadingIcon(Icons.Filled.Diversity3) },
                showDivider = true,
            )
            SettingsRow(
                title = "角色管理",
                subtitle = "查看系统角色及各角色用户数",
                onClick = onOpenRoles,
                leading = { LeadingIcon(Icons.Filled.Shield) },
                showDivider = true,
            )
            SettingsRow(
                title = "课程管理",
                subtitle = "系统预置课程目录（学科 / 课程方式 / 默认积分）",
                onClick = onOpenCourses,
                leading = { LeadingIcon(Icons.Filled.School) },
                showDivider = false,
            )
        }
        SettingsSectionLabel("验证码渠道")
        SettingsCard {
            SettingsRow(
                title = "短信配置",
                subtitle = "阿里云 / 腾讯云 / 华为云 多 provider",
                onClick = onOpenSmsConfigs,
                leading = { LeadingIcon(Icons.Filled.Sms) },
                showDivider = true,
            )
            SettingsRow(
                title = "邮箱配置",
                subtitle = "SMTP / 阿里云 / 腾讯云 / 华为云",
                onClick = onOpenEmailConfigs,
                leading = { LeadingIcon(Icons.Filled.Email) },
                showDivider = false,
            )
        }
        Spacer(Modifier.height(8.dp))
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
