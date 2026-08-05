package com.myhome.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.myhome.net.dto.SystemUserDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDetailScreen(
    familyId: String,
    onBack: () -> Unit,
    vm: FamilyDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId) { vm.load(familyId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    var pendingRemove by remember { mutableStateOf<SystemUserDto?>(null) }
    var pendingDeleteFamily by remember { mutableStateOf(false) }

    if (state.deleted) {
        LaunchedEffect(Unit) { onBack() }
    }

    if (state.showAddMemberDialog) {
        AddMemberDialog(
            users = state.availableUsers,
            adding = state.actionInProgress,
            error = state.actionError,
            onSelect = { vm.addMember(it.id) },
            onDismiss = vm::closeAddMemberDialog,
        )
    }

    pendingRemove?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移出家庭") },
            text = { Text("确定把「${m.displayName}（${m.username}）」从当前家庭移出吗？该用户将变为无家庭、无角色状态。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = null
                    vm.removeMember(m.id)
                }) { Text("移出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            },
        )
    }

    if (pendingDeleteFamily) {
        AlertDialog(
            onDismissRequest = { pendingDeleteFamily = false },
            title = { Text("删除家庭") },
            text = {
                Text(
                    if (state.family?.members.isNullOrEmpty()) {
                        "确定删除家庭「${state.family?.name ?: ""}」吗？此操作不可恢复。"
                    } else {
                        "家庭还有 ${state.family?.memberCount ?: 0} 个成员，请先在下方「移出」所有成员后再删除家庭。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.deleting && state.family?.members.isNullOrEmpty(),
                    onClick = {
                        pendingDeleteFamily = false
                        vm.deleteFamily()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFamily = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.family?.name ?: "家庭详情",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::openAddMemberDialog) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "添加成员")
                    }
                    IconButton(onClick = { pendingDeleteFamily = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除家庭")
                    }
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
                state.error != null -> ErrorState(state.error!!, onRetry = vm::refresh)
                state.family == null -> EmptyState(title = "家庭不存在", modifier = Modifier.fillMaxSize())
                else -> {
                    val fam = state.family!!
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            SettingsSectionLabel("基本信息")
                            SettingsCard {
                                SettingsRow(
                                    title = "家庭 ID",
                                    trailing = {
                                        Text(
                                            text = fam.id,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        )
                                    },
                                    showDivider = true,
                                )
                                SettingsRow(
                                    title = "家庭名称",
                                    trailing = { Text(fam.name) },
                                    showDivider = true,
                                )
                                SettingsRow(
                                    title = "成员数",
                                    trailing = { Text("${fam.memberCount} 人") },
                                    showDivider = true,
                                )
                                SettingsRow(
                                    title = "创建时间",
                                    trailing = { Text(fam.createdAt.take(10)) },
                                    showDivider = false,
                                )
                            }
                        }
                        item { SettingsSectionLabel("成员（${fam.members.size}）") }
                        if (fam.members.isEmpty()) {
                            item {
                                SettingsCard {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "这个家庭还没有成员",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(fam.members, key = { it.id }) { m ->
                                MemberRow(
                                    member = m,
                                    onRemove = { pendingRemove = m },
                                )
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { pendingDeleteFamily = true },
                                enabled = !state.deleting,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.deleting) {
                                    Text("删除中…")
                                } else {
                                    Text("删除家庭")
                                }
                            }
                        }
                        state.actionError?.let { err ->
                            item {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: SystemUserDto, onRemove: () -> Unit) {
    val roleLabel = com.myhome.util.RoleUtil.label(member.roles)
    SettingsCard {
        SettingsRow(
            title = "${member.displayName}（${member.username}）",
            subtitle = "$roleLabel · ${if (member.isActive) "启用" else "停用"}",
            onClick = null,
            trailing = {
                TextButton(onClick = onRemove) {
                    Text("移出", color = MaterialTheme.colorScheme.error)
                }
            },
            showDivider = false,
        )
    }
}

@Composable
private fun AddMemberDialog(
    users: List<SystemUserDto>,
    adding: Boolean,
    error: String?,
    onSelect: (SystemUserDto) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加成员") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (users.isEmpty()) {
                    Text(
                        text = "暂无可添加的用户（仅显示无家庭且非系统管理员的用户）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(users, key = { it.id }) { u ->
                            SettingsCard {
                                SettingsRow(
                                    title = "${u.displayName}（${u.username}）",
                                    subtitle = com.myhome.util.RoleUtil.label(u.roles),
                                    onClick = { if (!adding) onSelect(u) },
                                    showDivider = false,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
