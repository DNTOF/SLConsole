package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.bottomChromePadding
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.withBottomChrome
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.UiColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerChips(server: ServerConfig) {
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InfoChip(
            if (server.controlTransport == "ws") "WS" else "HTTP",
            if (server.controlTransport == "ws") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
        )
        InfoChip(
            if (server.verifyToken.isNotBlank()) "监控✓" else "监控✗",
            if (server.verifyToken.isNotBlank()) UiColors.Online else UiColors.Offline,
        )
        InfoChip(
            if (server.hasControl) "控制✓" else "控制✗",
            if (server.hasControl) UiColors.Online else UiColors.Offline,
        )
    }
}

@Composable
fun ServersScreen(onEdit: (String) -> Unit, onAdd: () -> Unit) {
    val store = ServiceLocator.serverStore
    val servers by store.serversFlow.collectAsState(initial = emptyList())
    val activeId by store.activeIdFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<ServerConfig?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (servers.isEmpty()) {
            EmptyState(
                Icons.Outlined.Dns,
                "还没有服务器",
                "添加一个运行 SLDataAPI 插件的游戏服务器",
                "添加服务器",
                onAdd,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp).withBottomChrome(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(servers.size, key = { servers[it].id }) { index ->
                    val server = servers[index]
                    val isActive = server.id == activeId
                    AppSurface(
                        Modifier.fillMaxWidth(),
                        onClick = { scope.launch { store.setActive(server.id) } },
                        role = GlassRole.Panel,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape,
                                        ),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(server.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        "${server.addressText} · 每 ${server.refetchIntervalMs / 1000}s 轮询",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onEdit(server.id) }) {
                                    Icon(Icons.Filled.Edit, "编辑")
                                }
                                IconButton(onClick = { deleteTarget = server }) {
                                    Icon(Icons.Outlined.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            ServerChips(server)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .bottomChromePadding()
                .padding(end = 20.dp, bottom = 8.dp),
        ) {
            Icon(Icons.Filled.Add, "添加服务器")
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除服务器 ${target.displayName}?",
            text = "本地保存的连接配置与凭据将被移除,游戏服务器本身不受影响。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                scope.launch {
                    ControlRepository.closeServer(target.id)
                    store.delete(target.id)
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
