package com.myhome.ui.mine

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.BuildConfig
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

@Composable
fun VersionInfoScreen(
    onBack: () -> Unit,
    vm: VersionInfoViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    SettingsScaffold(title = "版本信息", onBack = onBack) {
        SettingsSectionLabel("当前版本")
        SettingsCard {
            SettingsRow(
                title = "已安装版本",
                trailing = { Text("v${BuildConfig.VERSION_NAME}") },
                showDivider = false,
            )
        }
        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("服务器版本")
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = vm::refresh,
            )
            state.info != null -> SettingsCard {
                SettingsRow(
                    title = "最新版本",
                    trailing = {
                        Text(
                            text = "v${state.info!!.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    showDivider = true,
                )
                SettingsRow(
                    title = "发布日期",
                    trailing = {
                        Text(
                            text = state.info!!.releaseDate ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    showDivider = true,
                )
                SettingsRow(
                    title = "APK 下载",
                    trailing = {
                        Text(
                            text = state.info!!.apkUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    showDivider = true,
                )
                SettingsRow(
                    title = "版本说明",
                    subtitle = state.info!!.description ?: "暂无版本描述",
                    showDivider = false,
                )
            }
        }
    }
}
