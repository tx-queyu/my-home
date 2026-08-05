package com.myhome.ui.education

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.RedemptionDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedemptionListScreen(
    onBack: () -> Unit,
    vm: RedemptionListViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "兑换记录",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                state.items.isEmpty() -> EmptyState(
                    title = "暂无兑换记录",
                    description = "在奖励列表兑换后会在这里显示",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { redemption ->
                        RedemptionCard(
                            redemption = redemption,
                            isParent = state.isParent,
                            onFulfill = { vm.fulfill(redemption.id) },
                            onReject = { vm.reject(redemption.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RedemptionCard(
    redemption: RedemptionDto,
    isParent: Boolean,
    onFulfill: () -> Unit,
    onReject: () -> Unit,
) {
    SettingsCard {
        SettingsRow(
            title = redemption.rewardName ?: "奖励",
            subtitle = buildString {
                append("${redemption.cost} 积分")
                redemption.createdAt?.let { append(" · $it") }
            },
            onClick = null,
            trailing = { StatusPill(redemption.status) },
            showDivider = false,
        )
        if (isParent && redemption.status == "pending") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onFulfill, modifier = Modifier.weight(1f)) { Text("已发放") }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("拒绝") }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, color) = when (status) {
        "pending" -> "待发放" to MaterialTheme.colorScheme.surfaceVariant
        "fulfilled" -> "已发放" to MaterialTheme.colorScheme.primary
        "rejected" -> "已拒绝" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (status) {
        "fulfilled" -> MaterialTheme.colorScheme.onPrimary
        "rejected" -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
