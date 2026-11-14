// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import com.microsoft.clarity.modifiers.clarityMask
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.ServerData
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.withBottomChrome
import com.dntof.slconsole.ui.Routes
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.ErrorPanel
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.PromptDialog
import com.dntof.slconsole.ui.components.ShortcutTile
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString
import androidx.annotation.StringRes

@Composable
fun DashboardScreen(
    onOpenPlayers: () -> Unit,
    onOpenControl: () -> Unit,
    onAddServer: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val monitorState by MonitorEngine.state.collectAsState()
    when (val state = monitorState) {
        is MonitorEngine.MonitorState.Idle -> EmptyState(
            Icons.Outlined.SpaceDashboard,
            stringResource(R.string.common_no_servers_yet),
            stringResource(R.string.dashboard_add_game_server_running_sldataapi),
            stringResource(R.string.common_add_server),
            onAddServer,
        )
        is MonitorEngine.MonitorState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is MonitorEngine.MonitorState.Error -> ErrorPanel(state.message) { MonitorEngine.refreshNow() }
        is MonitorEngine.MonitorState.Ready -> DashboardContent(state, onOpenPlayers, onOpenControl, onNavigate)
    }
}

@Composable
private fun DashboardContent(
    state: MonitorEngine.MonitorState.Ready,
    onOpenPlayers: () -> Unit,
    onOpenControl: () -> Unit,
    onNavigate: (String) -> Unit,
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
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(state, data, server, now) }
        item { StatGrid(data, state, now) }
        item { ShortcutGrid(onNavigate) }
        item {
            SectionCard(stringResource(R.string.dashboard_teams), subtitle = pluralStringResource(R.plurals.dashboard_players_total, data.playersCount.toInt(), data.playersCount)) {
                TeamBar(stringResource(R.string.dashboard_class_d), data.dCount, data.playersCount, teamColor("D级"))
                TeamBar(stringResource(R.string.dashboard_foundation), data.foundationCount, data.playersCount, teamColor("基金会"))
                TeamBar("SCP", data.scpCount, data.playersCount, teamColor("SCP"))
                TeamBar(stringResource(R.string.dashboard_spectators), data.spectatorCount, data.playersCount, teamColor("观众"))
            }
        }
        if (server?.hasControl == true) {
            item {
                SectionCard(
                    stringResource(R.string.dashboard_quick_actions),
                    actions = { TextButton(onClick = onOpenControl) { Text(stringResource(R.string.dashboard_all_controls)) } },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showBroadcast = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Campaign, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.common_broadcast))
                        }
                        OutlinedButton(onClick = { showRestart = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.common_restart_round))
                        }
                    }
                }
            }
        }
        if (data.players.isNotEmpty()) {
            item {
                SectionCard(
                    stringResource(R.string.dashboard_online_players),
                    actions = { TextButton(onClick = onOpenPlayers) { Text(stringResource(R.string.dashboard_all, data.players.size)) } },
                ) {
                    data.players.take(5).forEach { player ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clarityMask(),
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
                            InfoChip(player.team.ifBlank { stringResource(R.string.common_unknown) }, teamColor(player.team))
                        }
                    }
                }
            }
        }
        if (data.adaptedPlugins.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.common_adapted_plugins)) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        data.adaptedPlugins.forEach { plugin ->
                            InfoChip(
                                "${plugin.name ?: plugin.id} ${plugin.version ?: ""}".trim(),
                                onClick = if (plugin.id.isBlank()) null else {
                                    { onNavigate(Routes.adaptedDetail(plugin.id)) }
                                },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (showBroadcast) {
        PromptDialog(
            title = stringResource(R.string.common_broadcast),
            subtitle = stringResource(R.string.dashboard_shown_center_every_players_screen),
            confirmLabel = stringResource(R.string.common_send),
            fields = listOf(
                PromptField("message", stringResource(R.string.common_broadcast_message)),
                PromptField("duration", stringResource(R.string.common_seconds_display_1_60), initial = "5", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            ),
            onConfirm = { values ->
                val message = values["message"].orEmpty().trim()
                if (message.isEmpty()) {
                    scope.launch { snackbar.showSnackbar(appString(R.string.dashboard_broadcast_empty_not_sent)) }
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
                        snackbar.showOutcome(outcome, appString(R.string.common_broadcast_sent))
                    }
                }
            },
            onDismiss = { showBroadcast = false },
        )
    }
    if (showRestart) {
        com.dntof.slconsole.ui.components.ConfirmDialog(
            title = stringResource(R.string.dashboard_restart_current_round),
            text = stringResource(R.string.dashboard_all_players_will_moved_back),
            confirmLabel = stringResource(R.string.dashboard_restart),
            danger = true,
            onConfirm = {
                scope.launch {
                    val outcome = ControlRepository.call(
                        server!!,
                        "/control/round",
                        buildJsonObject { put("action", "restart") },
                    )
                    snackbar.showOutcome(outcome, appString(R.string.dashboard_round_restarted))
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
    val nukeActive = !data.nukeStatus.isNullOrBlank() && data.nukeStatus != "未激活"
    AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Panel) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (data.online) UiColors.Online else UiColors.Offline, 14.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clarityMask()) {
                    Text(
                        data.serverName ?: server?.displayName ?: stringResource(R.string.dashboard_unknown_server),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        server?.addressText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${data.playersCount}/${data.maxPlayers}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (data.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.dashboard_online_players), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(if (data.online) stringResource(R.string.dashboard_online) else stringResource(R.string.dashboard_offline), if (data.online) UiColors.Online else UiColors.Offline)
                InfoChip(data.currentPhase ?: stringResource(R.string.dashboard_unknown_phase), MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Warning,
                    null,
                    Modifier.size(16.dp),
                    tint = if (nukeActive) UiColors.Nuke else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (nukeActive) {
                        val countdown = data.nukeCountdown?.takeIf { it > 0 }?.let { stringResource(R.string.dashboard_left, it) } ?: ""
                        "${data.nukeStatus}$countdown"
                    } else {
                        stringResource(R.string.dashboard_warhead_not_active)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nukeActive) UiColors.Nuke else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dashboard_polling_every_updated, server?.refetchIntervalMs?.div(1000) ?: 5, Format.relative(state.fetchedAt, now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatGrid(
    data: ServerData,
    state: MonitorEngine.MonitorState.Ready,
    now: Long,
) {
    val duration = if (data.roundStarted) {
        val live = data.roundDuration + ((now - state.fetchedAt).coerceAtLeast(0) / 1000).toInt()
        Format.duration(live)
    } else {
        stringResource(R.string.common_not_started)
    }
    val stats = listOf(
        stringResource(R.string.common_phase) to (data.currentPhase ?: stringResource(R.string.common_unknown)),
        stringResource(R.string.dashboard_round) to duration,
        stringResource(R.string.dashboard_latency) to "${data.ping} ms",
        stringResource(R.string.dashboard_round_trip) to "${state.rttMs} ms",
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 720.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) ->
                        AppSurface(Modifier.weight(1f), role = GlassRole.Row) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    value,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class DashShortcut(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val DASH_SHORTCUTS = listOf(
    DashShortcut(Routes.PLAYERS, R.string.common_players, Icons.Outlined.Groups),
    DashShortcut(Routes.CONSOLE, R.string.common_console, Icons.Outlined.Terminal),
    DashShortcut(Routes.MAPS, R.string.common_map, Icons.Outlined.Map),
    DashShortcut(Routes.EVENTS, R.string.dashboard_events, Icons.Outlined.Bolt),
    DashShortcut(Routes.REMOTE, R.string.dashboard_remote, Icons.Outlined.SettingsRemote),
    DashShortcut(Routes.VOICE, R.string.dashboard_voice, Icons.Outlined.GraphicEq),
    DashShortcut(Routes.BANS, R.string.dashboard_bans, Icons.Outlined.Gavel),
    DashShortcut(Routes.LOGS, R.string.dashboard_logs, Icons.AutoMirrored.Outlined.Article),
)

@Composable
private fun ShortcutGrid(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.dashboard_go), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 840.dp) 8 else 4
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DASH_SHORTCUTS.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            ShortcutTile(
                                icon = item.icon,
                                label = stringResource(item.label),
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(item.route) },
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
