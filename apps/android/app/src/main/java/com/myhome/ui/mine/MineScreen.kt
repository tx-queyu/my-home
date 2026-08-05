package com.myhome.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.BuildConfig
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    onOpenSettings: () -> Unit,
    onOpenChangePhone: () -> Unit,
    onOpenChangeEmail: () -> Unit,
    vm: MineViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = vm::refresh,
                modifier = Modifier.padding(padding),
            )
            else -> MineContent(
                state = state,
                onOpenChangePhone = onOpenChangePhone,
                onOpenChangeEmail = onOpenChangeEmail,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun MineContent(
    state: MineUiState,
    onOpenChangePhone: () -> Unit,
    onOpenChangeEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = remember(state.displayName, state.username) {
        val src = state.displayName.ifBlank { state.username }
        src.firstOrNull()?.uppercase() ?: "?"
    }
    val roleLabel = com.myhome.util.RoleUtil.label(state.roles)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.displayName.ifBlank { state.username },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        if (state.familyName.isNotBlank()) {
            Text(
                text = state.familyName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        ContactCard(
            phone = state.phone,
            phoneVerified = state.phoneVerified,
            email = state.email,
            emailVerified = state.emailVerified,
            onOpenChangePhone = onOpenChangePhone,
            onOpenChangeEmail = onOpenChangeEmail,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun ContactCard(
    phone: String?,
    phoneVerified: Boolean,
    email: String?,
    emailVerified: Boolean,
    onOpenChangePhone: () -> Unit,
    onOpenChangeEmail: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val phoneDisplay = when {
            phone.isNullOrBlank() -> "未绑定"
            !phoneVerified -> "$phone（未验证）"
            else -> phone
        }
        val emailDisplay = when {
            email.isNullOrBlank() -> "未绑定"
            !emailVerified -> "$email（未验证）"
            else -> email
        }
        ContactRow(
            title = "手机号",
            subtitle = phoneDisplay,
            onClick = onOpenChangePhone,
        )
        ContactRow(
            title = "邮箱",
            subtitle = emailDisplay,
            onClick = onOpenChangeEmail,
        )
    }
}

@Composable
private fun ContactRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    com.myhome.ui.components.SettingsCard {
        com.myhome.ui.components.SettingsRow(
            title = title,
            subtitle = subtitle,
            onClick = onClick,
            showDivider = false,
        )
    }
}
