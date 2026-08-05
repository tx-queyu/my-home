package com.myhome.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.PointTransactionDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsScreen(
    onBack: () -> Unit,
    vm: PointsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "积分明细",
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { BalanceCard(state.balance) }
                    if (state.transactions.isEmpty()) {
                        item {
                            EmptyState(
                                title = "暂无积分记录",
                                description = "完成任务后会有积分流水在这里显示",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        items(state.transactions, key = { it.id }) { txn ->
                            TransactionCard(txn)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: Int) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                "当前积分",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$balance",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TransactionCard(txn: PointTransactionDto) {
    val positive = txn.delta >= 0
    val sign = if (positive) "+" else ""
    val sourceCn = when (txn.source) {
        "task" -> "任务"
        "redemption" -> "兑换"
        "adjustment" -> "调整"
        else -> txn.source
    }
    SettingsCard {
        SettingsRow(
            title = txn.note ?: sourceCn,
            subtitle = txn.createdAt ?: "",
            onClick = null,
            leading = {
                Icon(
                    imageVector = if (positive) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
            trailing = {
                Text(
                    "${sign}${txn.delta}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            },
            showDivider = false,
        )
    }
}
