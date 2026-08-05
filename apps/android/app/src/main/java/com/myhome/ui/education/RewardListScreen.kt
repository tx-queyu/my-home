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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.myhome.net.dto.RewardDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenRedemptions: () -> Unit,
    vm: RewardListViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it) }
    }

    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var confirmRedeem by remember { mutableStateOf<String?>(null) }
    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除该奖励？") },
            text = { Text("此操作不可撤销") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(confirmDelete!!)
                    confirmDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
    if (confirmRedeem != null) {
        val reward = state.items.firstOrNull { it.id == confirmRedeem }
        AlertDialog(
            onDismissRequest = { confirmRedeem = null },
            title = { Text("兑换：${reward?.name ?: ""}") },
            text = { Text("将扣除 ${reward?.cost ?: 0} 积分，提交后家长会处理") },
            confirmButton = {
                TextButton(onClick = {
                    vm.redeem(confirmRedeem!!)
                    confirmRedeem = null
                }) { Text("确认兑换") }
            },
            dismissButton = { TextButton(onClick = { confirmRedeem = null }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "奖励",
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
                    TextButton(onClick = onOpenRedemptions) { Text("兑换记录") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state.isParent) {
                FloatingActionButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, "新建奖励")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                state.items.isEmpty() -> EmptyState(
                    title = "还没有奖励",
                    description = if (state.isParent) "点击右下角加号添加一个" else "等待家长添加奖励",
                    actionLabel = if (state.isParent) "添加奖励" else null,
                    onAction = if (state.isParent) onCreate else null,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { BalanceHeader(state.balance) }
                    items(state.items, key = { it.id }) { reward ->
                        RewardCard(
                            reward = reward,
                            balance = state.balance,
                            isParent = state.isParent,
                            onRedeem = { confirmRedeem = reward.id },
                            onDelete = { confirmDelete = reward.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHeader(balance: Int) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
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
private fun RewardCard(
    reward: RewardDto,
    balance: Int,
    isParent: Boolean,
    onRedeem: () -> Unit,
    onDelete: () -> Unit,
) {
    val affordable = balance >= reward.cost
    val outOfStock = reward.stock == 0
    SettingsCard {
        SettingsRow(
            title = reward.name,
            subtitle = buildString {
                append("需要 ${reward.cost} 积分")
                reward.stock?.let { append(" · 库存 $it") }
                if (!reward.isActive) append(" · 已下架")
            },
            onClick = null,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (outOfStock) {
                        StockPill("缺货")
                    } else if (reward.isActive) {
                        StockPill("有货")
                    } else {
                        StockPill("下架")
                    }
                    if (isParent) {
                        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
                    }
                }
            },
            showDivider = false,
        )
        if (!isParent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onRedeem,
                    enabled = affordable && reward.isActive && !outOfStock,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (affordable && !outOfStock) "兑换" else "积分不足") }
            }
        }
    }
}

@Composable
private fun StockPill(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
