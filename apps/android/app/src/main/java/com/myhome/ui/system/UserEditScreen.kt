package com.myhome.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import com.myhome.ui.components.SettingsScaffold
import com.myhome.ui.components.SettingsSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditScreen(
    userId: String,
    onBack: () -> Unit,
    vm: UserEditViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val isCreate = userId.isBlank()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(userId) { vm.load(userId) }
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it) } }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); vm.consumeToast() }
    }

    SettingsScaffold(
        title = if (isCreate) "新建用户" else "编辑用户",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) {
        if (state.loading) {
            LoadingState()
            return@SettingsScaffold
        }
        if (!isCreate && state.user == null) {
            SettingsCard {
                SettingsRow(
                    title = state.error ?: "用户不存在",
                    onClick = null,
                    showDivider = false,
                    titleColor = MaterialTheme.colorScheme.error,
                )
            }
            return@SettingsScaffold
        }

        SettingsSectionLabel("用户信息")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = vm::onUsernameChange,
                    label = { Text("用户名") },
                    singleLine = true,
                    enabled = isCreate,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isCreate) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = vm::onPasswordChange,
                        label = { Text("初始密码（至少 6 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = vm::onDisplayNameChange,
                    label = { Text("昵称") },
                    singleLine = true,
                    enabled = isCreate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("角色")
        SettingsCard {
            if (state.roleOptions.isEmpty()) {
                SettingsRow(
                    title = "暂无可选角色",
                    onClick = null,
                    showDivider = false,
                )
            } else {
                val groupMembers = remember(state.roleOptions) {
                    state.roleOptions
                        .filter { it.exclusiveGroup != null }
                        .groupBy { it.exclusiveGroup!! }
                }
                state.roleOptions.forEachIndexed { index, role ->
                    val roleLabel = com.myhome.util.RoleUtil.label(role.role)
                    val desc = role.description.substringAfter("：", role.description)
                    val siblings = groupMembers[role.exclusiveGroup]
                        ?.filter { it.role != role.role }
                        ?.map { com.myhome.util.RoleUtil.label(it.role) }
                        ?: emptyList()
                    val groupHint = if (siblings.isNotEmpty()) {
                        " · 与「${siblings.joinToString("」「")}」互斥"
                    } else ""
                    SettingsRow(
                        title = roleLabel,
                        subtitle = "$desc$groupHint",
                        onClick = { vm.onRoleToggle(role.role) },
                        trailing = {
                            if (role.role in state.roles) {
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        },
                        showDivider = index < state.roleOptions.size - 1,
                    )
                }
            }
            if ("admin" in state.roles) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "系统管理员不属于任何家庭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.roles.any { it != "admin" }) {
            Spacer(Modifier.height(8.dp))
            SettingsSectionLabel("所属家庭")
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    var familyMenuExpanded by remember { mutableStateOf(false) }
                    var familyQuery by remember { mutableStateOf("") }
                    val selectedFamily = state.families.firstOrNull { it.id == state.familyId }
                    val familyFieldValue = if (familyMenuExpanded) familyQuery else (selectedFamily?.name ?: "")
                    val filteredFamilies = if (familyQuery.isBlank()) state.families
                        else state.families.filter { it.name.contains(familyQuery, ignoreCase = true) }
                    ExposedDropdownMenuBox(
                        expanded = familyMenuExpanded,
                        onExpandedChange = { familyMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = familyFieldValue,
                            onValueChange = {
                                familyQuery = it
                                familyMenuExpanded = true
                            },
                            label = { Text("所属家庭") },
                            placeholder = if (selectedFamily == null && !familyMenuExpanded) {
                                { Text("请选择家庭") }
                            } else null,
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = familyMenuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                        )
                        ExposedDropdownMenu(
                            expanded = familyMenuExpanded,
                            onDismissRequest = {
                                familyMenuExpanded = false
                                familyQuery = ""
                            },
                        ) {
                            if (filteredFamilies.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (familyQuery.isBlank()) "暂无家庭" else "无匹配家庭")
                                    },
                                    onClick = {},
                                    enabled = false,
                                )
                            } else {
                                filteredFamilies.forEach { family ->
                                    DropdownMenuItem(
                                        text = { Text(family.name) },
                                        onClick = {
                                            vm.onFamilyChange(family.id)
                                            familyMenuExpanded = false
                                            familyQuery = ""
                                        },
                                        trailingIcon = {
                                            if (family.id == state.familyId) {
                                                Text(
                                                    "✓",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("状态")
        SettingsCard {
            SettingsRow(
                title = "启用",
                subtitle = "停用的用户无法登录",
                onClick = null,
                trailing = {
                    Switch(
                        checked = state.isActive,
                        onCheckedChange = vm::onActiveChange,
                    )
                },
                showDivider = false,
            )
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
        val hasFamilyRole = state.roles.any { it != "admin" }
        val familyOk = !hasFamilyRole || state.familyId != null
        val createValid = state.username.trim().length >= 3 &&
            state.password.length >= 6 &&
            state.displayName.trim().isNotBlank() &&
            familyOk
        val editValid = familyOk
        val canSubmit = if (isCreate) createValid else editValid
        Button(
            onClick = vm::save,
            enabled = canSubmit && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.saving) "保存中…" else if (isCreate) "创建" else "保存")
        }
        if (!isCreate) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showResetDialog = true },
                enabled = !state.resetting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.resetting) "重置中…" else "重置密码")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                enabled = !state.deleting,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.deleting) "删除中…" else "删除用户")
            }
        }
        Spacer(Modifier.height(24.dp))

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除用户") },
                text = {
                    val name = state.displayName.ifBlank { state.username }
                    Text("确定要删除「$name」吗？\n\n该用户的所有任务记录、积分流水、兑换记录都将被清除，此操作不可撤销。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            vm.delete()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }

        if (showResetDialog) {
            var resetPwd by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { if (!state.resetting) showResetDialog = false },
                title = { Text("重置密码") },
                text = {
                    val name = state.displayName.ifBlank { state.username }
                    Column {
                        Text("为「$name」设置新密码，确认后该用户即可使用新密码登录。")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = resetPwd,
                            onValueChange = { resetPwd = it },
                            label = { Text("新密码（至少 6 位）") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !state.resetting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !state.resetting && resetPwd.length >= 6,
                        onClick = {
                            vm.resetPassword(resetPwd)
                            showResetDialog = false
                        },
                    ) { Text(if (state.resetting) "重置中…" else "重置") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetDialog = false },
                        enabled = !state.resetting,
                    ) { Text("取消") }
                },
            )
        }
    }
}

