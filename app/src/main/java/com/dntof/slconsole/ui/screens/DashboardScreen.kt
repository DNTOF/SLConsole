package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.ServerData
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.ErrorPanel
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.components.PromptDialog
import com.dntof.slconsole.ui.components.PromptField
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.StatusDot
import com.dntof.slconsole.ui.components.TeamBar
import com.dntof.slconsole.ui.components.UiColors
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.components.teamColor
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun DashboardScreen(
    onOpenPlayers: () -> Unit,
    onOpenControl: () -> Unit,
    onAddServer: () -> Unit,
) {
    val monitorState by MonitorEngine.state.collectAsState()
    when (val state = monitorState) {
        is MonitorEngine.MonitorState.Idle -> EmptyState(
            Icons.Outlined.SpaceDashboard,
            "还没有服务器",
            "添加一个运行 SLDataAPI 插件的游戏服务器,开始掌上管理",
            "添加服务器",
            onAddServer,
        )
        is MonitorEngine.MonitorState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is MonitorEngine.MonitorState.Error -> ErrorPanel(state.message) { MonitorEngine.refreshNow() }
        is MonitorEngine.MonitorState.Ready -> DashboardContent(state, onOpenPlayers, onOpenControl)
    }
}

@Composable
private fun DashboardContent(
    state: MonitorEngine.MonitorState.Ready,
    onOpenPlayers: () -> Unit,
    onOpenControl: () -> Unit,
) {
    val server = rememberActiveServer()
    val data = state.data
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    var showBroadcast by remember { mutableStateOf(false) }
    var showRestart by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(state, data, server, now) }
        item { RoundCard(data, state.fetchedAt, now) }
        item {
            SectionCard("阵营分布", subtitle = "共 ${data.playersCount} 名玩家") {
                TeamBar("D级人员", data.dCount, data.playersCount, teamColor("D级"))
                TeamBar("基金会", data.foundationCount, data.playersCount, teamColor("基金会"))
                TeamBar("SCP", data.scpCount, data.playersCount, teamColor("SCP"))
                TeamBar("观众", data.spectatorCount, data.playersCount, teamColor("观众"))
            }
        }
        if (server?.hasControl == true) {
            item {
                SectionCard(
                    "快捷操作",
                    actions = { TextButton(onClick = onOpenControl) { Text("全部控制") } },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showBroadcast = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Campaign, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("全服广播")
                        }
                        OutlinedButton(onClick = { showRestart = true }, modifier = Modifier.weight(1f)) {
                            Text("重启回合")
                        }
                    }
                }
            }
        }
        if (data.players.isNotEmpty()) {
            item {
                SectionCard(
                    "在线玩家",
                    actions = { TextButton(onClick = onOpenPlayers) { Text("全部(${data.players.size})") } },
                ) {
                    data.players.take(5).forEach { player ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    player.nickname.firstOrNull()?.toString() ?: "?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(player.nickname, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    player.role,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            InfoChip(player.team.ifBlank { "未知" }, teamColor(player.team))
                        }
                    }
                }
            }
        }
        if (data.adaptedPlugins.isNotEmpty()) {
            item {
                SectionCard("适配插件") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        data.adaptedPlugins.forEach { plugin ->
                            InfoChip("${plugin.name ?: plugin.id} ${plugin.version ?: ""}".trim())
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (showBroadcast) {
        PromptDialog(
            title = "全服广播",
            subtitle = "显示在所有玩家屏幕中央,内容不超过 500 字符",
            confirmLabel = "发送",
            fields = listOf(
                PromptField("message", "广播内容"),
                PromptField("duration", "显示秒数(1-60)", initial = "5", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            ),
            onConfirm = { values ->
                val message = values["message"].orEmpty().trim()
                if (message.isEmpty()) {
                    scope.launch { snackbar.showSnackbar("广播内容为空,未发送") }
                } else {
                    scope.launch {
                        val outcome = ControlRepository.call(
                            server!!,
                            "/control/broadcast",
                            buildJsonObject {
                                put("message", message)
                                put("duration_seconds", values["duration"]?.toFloatOrNull() ?: 5f)
                                put("clear_previous", false)
                            },
                        )
                        snackbar.showOutcome(outcome, "广播已发送")
                    }
                }
            },
            onDismiss = { showBroadcast = false },
        )
    }
    if (showRestart) {
        com.dntof.slconsole.ui.components.ConfirmDialog(
            title = "重启当前回合?",
            text = "所有玩家将被移回等待阶段,进行中的回合进度丢失。",
            confirmLabel = "重启",
            danger = true,
            onConfirm = {
                scope.launch {
                    val outcome = ControlRepository.call(
                        server!!,
                        "/control/round",
                        buildJsonObject { put("action", "restart") },
                    )
                    snackbar.showOutcome(outcome, "回合已重启")
                }
            },
            onDismiss = { showRestart = false },
        )
    }
}

@Composable
private fun StatusCard(
    state: MonitorEngine.MonitorState.Ready,
    data: ServerData,
    server: ServerConfig?,
    now: Long,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(if (data.online) UiColors.Online else UiColors.Offline),
        )
        Column(Modifier.padding(start = 14.dp, top = 16.dp, bottom = 16.dp, end = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (data.online) UiColors.Online else UiColors.Offline, 14.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    data.serverName ?: server?.displayName ?: "未知服务器",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    server?.addressText ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${data.playersCount}/${data.maxPlayers}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (data.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("在线玩家", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(data.currentPhase ?: "未知阶段", MaterialTheme.colorScheme.primary)
            InfoChip("延迟 ${data.ping} ms")
            InfoChip("请求 ${state.rttMs} ms")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "每 ${server?.refetchIntervalMs?.div(1000) ?: 5} 秒轮询 · ${Format.relative(state.fetchedAt, now)}更新",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        }
    }
}

@Composable
private fun RoundCard(data: ServerData, fetchedAt: Long, now: Long) {
    SectionCard("回合状态") {
        KeyValueRow("阶段", data.currentPhase ?: "未知")
        if (data.roundStarted) {
            val liveDuration = data.roundDuration + ((now - fetchedAt).coerceAtLeast(0) / 1000).toInt()
            KeyValueRow("已进行", Format.duration(liveDuration))
        }
        val nukeActive = !data.nukeStatus.isNullOrBlank() && data.nukeStatus != "未激活"
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Warning,
                null,
                Modifier.size(18.dp),
                tint = if (nukeActive) UiColors.Nuke else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (nukeActive) {
                    val countdown = data.nukeCountdown?.takeIf { it > 0 }?.let { " · 剩余 ${it}s" } ?: ""
                    "${data.nukeStatus}$countdown"
                } else {
                    "核弹未激活"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (nukeActive) UiColors.Nuke else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
