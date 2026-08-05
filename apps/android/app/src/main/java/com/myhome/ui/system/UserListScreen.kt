package com.myhome.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.SystemUserDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    onBack: () -> Unit,
    onOpenUserEdit: (String) -> Unit,
    onOpenUserCreate: () -> Unit,
    vm: UserListViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "用户管理",
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
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenUserCreate) {
                Icon(Icons.Filled.Add, contentDescription = "新建用户")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val listState = rememberLazyListState()

            FilterBar(state, vm)
            Text(
                text = "共 ${state.total} 人",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> LoadingState()
                    state.error != null ->
                        ErrorState(state.error!!, onRetry = { vm.refresh() })
                    state.users.isEmpty() ->
                        EmptyState(title = "没有匹配的用户", modifier = Modifier.fillMaxSize())
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.users, key = { it.id }) { user ->
                            UserRow(user, onClick = { onOpenUserEdit(user.id) })
                        }
                        item {
                            if (state.loadingMore) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingState()
                                }
                            } else if (!state.hasMore) {
                                Text(
                                    text = "已全部加载",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            val shouldLoadMore by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = listState.layoutInfo.totalItemsCount
                    total > 0 && last >= total - 3 &&
                        !state.loading && !state.loadingMore && state.hasMore
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) vm.loadMore()
            }
        }
    }
}

@Composable
private fun FilterBar(state: UserListUiState, vm: UserListViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("搜索用户名 / 昵称") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
            trailingIcon = if (state.query.isNotBlank()) {
                {
                    IconButton(onClick = {
                        vm.onQueryChange("")
                        vm.onSearchSubmit()
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除")
                    }
                }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.onSearchSubmit() }),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val familyLabel = when (state.familyId) {
                null -> "全部"
                "none" -> "无家庭"
                else -> state.families.firstOrNull { it.id == state.familyId }?.name ?: "?"
            }
            val familyOptions = buildList {
                add(null to "全部")
                add("none" to "无家庭")
                state.families.forEach { add(it.id to it.name) }
            }
            FilterDropdown(
                label = "家庭",
                selectedLabel = familyLabel,
                options = familyOptions,
                onSelect = vm::onFamilyChange,
                modifier = Modifier.weight(1f),
            )

            val roleOptions = listOf(
                null to "全部",
                "admin" to "系统管理员",
                "family_admin" to "家庭管理员",
                "parent" to "家长",
                "child" to "孩子",
            )
            FilterDropdown(
                label = "角色",
                selectedLabel = roleOptions.firstOrNull { it.first == state.role }?.second ?: "全部",
                options = roleOptions,
                onSelect = vm::onRoleChange,
                modifier = Modifier.weight(1f),
            )

            val activeOptions = listOf(
                "all" to "全部",
                "true" to "启用",
                "false" to "停用",
            )
            val activeSelected = when (state.active) {
                null -> "all"
                true -> "true"
                false -> "false"
            }
            FilterDropdown(
                label = "状态",
                selectedLabel = activeOptions.first { it.first == activeSelected }.second,
                options = activeOptions,
                onSelect = { v ->
                    vm.onActiveChange(
                        when (v) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun UserRow(user: SystemUserDto, onClick: () -> Unit) {
    val roleLabel = com.myhome.util.RoleUtil.label(user.roles)
    SettingsCard {
        SettingsRow(
            title = "${user.displayName}（${user.username}）",
            subtitle = buildString {
                append(roleLabel)
                append(" · ")
                append(if (user.isActive) "启用" else "停用")
                user.familyName?.let { append(" · $it") }
                if ("admin" in user.roles) append(" · 无家庭")
            },
            onClick = onClick,
            showDivider = false,
        )
    }
}
