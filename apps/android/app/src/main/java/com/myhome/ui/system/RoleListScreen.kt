package com.myhome.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhome.net.dto.SystemRoleDto
import com.myhome.ui.components.EmptyState
import com.myhome.ui.components.ErrorState
import com.myhome.ui.components.LoadingState
import com.myhome.ui.components.SettingsCard
import com.myhome.ui.components.SettingsRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleListScreen(
    onBack: () -> Unit,
    vm: RoleListViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val groupMembers = remember(state.roles) {
        state.roles
            .filter { it.exclusiveGroup != null }
            .groupBy { it.exclusiveGroup!! }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "角色管理",
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.refresh() })
                state.roles.isEmpty() -> EmptyState(title = "还没有角色", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.roles, key = { it.role }) { role ->
                        val siblings = groupMembers[role.exclusiveGroup]
                            ?.filter { it.role != role.role }
                            ?.map { com.myhome.util.RoleUtil.label(it.role) }
                            ?: emptyList()
                        RoleRow(role, siblings)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleRow(role: SystemRoleDto, siblings: List<String>) {
    val roleLabel = com.myhome.util.RoleUtil.label(role.role)
    val groupHint = if (siblings.isNotEmpty()) {
        " · 与「${siblings.joinToString("」「")}」互斥"
    } else ""
    SettingsCard {
        SettingsRow(
            title = roleLabel,
            subtitle = "${role.count} 人 · ${role.description}$groupHint",
            onClick = null,
            showDivider = false,
        )
    }
}
