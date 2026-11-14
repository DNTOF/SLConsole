// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.microsoft.clarity.modifiers.clarityMask
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.AdaptedPlugin
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.remote.BetaHints
import com.dntof.slconsole.data.model.OmegaWarheadSnapshot
import com.dntof.slconsole.data.model.SlPlayerSong
import com.dntof.slconsole.data.model.SlPlayerStatus
import com.dntof.slconsole.data.remote.SlHttpClient
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.ui.rememberServerBeta
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.ui.withBottomChrome
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString
import com.dntof.slconsole.ui.components.ButtonLabel

private const val SL_PLAYER_ID = "dntof.sl_player"
private const val OMEGA_ID = "dntof.omega_warhead"
private val http = SlHttpClient()

@Composable
fun AdaptedPluginsScreen(onOpen: (String) -> Unit) {
    val monitor by MonitorEngine.state.collectAsState()
    val beta = rememberServerBeta()
    val ready = monitor as? MonitorEngine.MonitorState.Ready
    val plugins = ready?.data?.adaptedPlugins.orEmpty()
    if (ready == null) {
        EmptyState(Icons.Outlined.Extension, stringResource(R.string.adapted_plugins_no_monitoring_data_yet), stringResource(R.string.adapted_plugins_once_connected_adapted_plugins_registered))
        return
    }
    if (plugins.isEmpty()) {
        EmptyState(
            Icons.Outlined.Extension,
            stringResource(R.string.adapted_plugins_no_adapted_plugins),
            stringResource(R.string.adapted_plugins_slplayer_omegawarhead_other_plugins_register),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.common_adapted_plugins), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.adapted_plugins_data_comes_from_get_sl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(plugins, key = { it.id.ifBlank { it.name ?: "" } }) { plugin ->
            AppSurface(
                Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(160)).fillMaxWidth(),
                onClick = { if (plugin.id.isNotBlank()) onOpen(plugin.id) },
                role = GlassRole.Panel,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(plugin.name ?: plugin.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        plugin.id.ifBlank { stringResource(R.string.adapted_plugins_no_id_provided) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!plugin.version.isNullOrBlank()) InfoChip(plugin.version)
                        InfoChip(pluginKind(plugin, beta))
                    }
                }
            }
        }
    }
}

@Composable
fun AdaptedPluginDetailScreen(pluginId: String) {
    val server = rememberActiveServer()
    val beta = rememberServerBeta()
    val monitor by MonitorEngine.state.collectAsState()
    val ready = monitor as? MonitorEngine.MonitorState.Ready
    val plugin = ready?.data?.adaptedPlugins?.firstOrNull { it.id.equals(pluginId, ignoreCase = true) }
    val dntof = ready?.data?.dntofPlugins
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(plugin?.name ?: pluginId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(pluginId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (plugin != null) {
            item { PluginMetaCard(plugin) }
        } else {
            item {
                SectionCard(stringResource(R.string.adapted_plugins_not_monitoring_snapshot_yet)) {
                    Text(
                        stringResource(R.string.adapted_plugins_wait_next_get_sl_data),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (pluginId.equals(SL_PLAYER_ID, true) || plugin?.capabilities?.any { it.contains("sl_player") } == true) {
            item { SlPlayerPanel(snapshot = dntof?.slPlayer?.nowPlaying) }
        }
        if (pluginId.equals(OMEGA_ID, true) || plugin?.capabilities?.any { it.contains("omega") } == true) {
            item { OmegaPanel(dntof?.omegaWarhead) }
        }
        if (plugin != null && beta.adaptedActions && plugin.actions.isNotEmpty()) {
            item { AdaptedActionsCard(plugin) }
        }
        if (plugin != null && plugin.routes.isNotEmpty()) {
            item { AdaptedRoutesCard(plugin, beta.adaptedActions) }
        }
    }
}

@Composable
private fun PluginMetaCard(plugin: AdaptedPlugin) {
    SectionCard(stringResource(R.string.adapted_plugins_registration), subtitle = "GET /get_sl_data · adapted_plugins") {
        KeyValueRow(stringResource(R.string.common_version), plugin.version ?: "-")
        if (plugin.capabilities.isEmpty()) {
            Text(stringResource(R.string.adapted_plugins_no_capabilities_declared), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                plugin.capabilities.take(6).forEach { InfoChip(it) }
            }
        }
        plugin.status?.let {
            Spacer(Modifier.height(8.dp))
            Text("status", style = MaterialTheme.typography.labelLarge)
            Text(
                it.toString(),
                modifier = Modifier.clarityMask(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SlPlayerPanel(snapshot: String?) {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current
    var status by remember { mutableStateOf<SlPlayerStatus?>(null) }
    var volume by remember { mutableFloatStateOf(50f) }
    var fetchUrl by remember { mutableStateOf("") }

    fun refresh() {
        val target = server ?: return
        scope.launch {
            when (val outcome = ControlRepository.call(target, "/control/plugins/slplayer", buildJsonObject { put("action", "status") })) {
                is ControlRepository.ControlOutcome.Success -> {
                    status = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(SlPlayerStatus.serializer(), it) }.getOrNull()
                    }
                    status?.let { volume = it.volume.toFloat() }
                }
                is ControlRepository.ControlOutcome.Failure -> snackbar.showSnackbar(outcome.message)
            }
        }
    }

    fun act(body: JsonObject, ok: String) {
        val target = server ?: return
        scope.launch {
            snackbar.showOutcome(ControlRepository.call(target, "/control/plugins/slplayer", body), ok)
            refresh()
        }
    }

    LaunchedEffect(server?.id) { if (server?.hasControl == true) refresh() }

    SectionCard(stringResource(R.string.adapted_plugins_slplayer_playback), subtitle = "POST /control/plugins/slplayer") {
        if (server?.hasControl != true) {
            Text(
                stringResource(R.string.adapted_plugins_api_key_required_control_track, snapshot ?: stringResource(R.string.adapted_plugins_not_playing)),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        val now = status
        Text(now?.song ?: snapshot ?: stringResource(R.string.adapted_plugins_not_playing), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            buildString {
                append(if (now?.playing == true) stringResource(R.string.adapted_plugins_playing) else appString(R.string.common_stopped))
                now?.source?.let { append(" · $it") }
                if (now != null && now.durationSeconds > 0) {
                    append(" · ${formatClock(now.elapsedSeconds)} / ${formatClock(now.durationSeconds)}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (now != null && now.durationSeconds > 0) {
            LinearProgressIndicator(
                progress = { (now.elapsedSeconds.toFloat() / now.durationSeconds).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
        Text(stringResource(R.string.adapted_plugins_volume, volume.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = {
                act(buildJsonObject {
                    put("action", "volume")
                    put("volume", volume.toInt())
                }, appString(R.string.adapted_plugins_volume_updated))
            },
            valueRange = 0f..100f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { act(buildJsonObject { put("action", "next") }, appString(R.string.adapted_plugins_skipped_next)) }, modifier = Modifier.weight(1f)) { ButtonLabel(appString(R.string.adapted_plugins_next)) }
            OutlinedButton(onClick = { act(buildJsonObject { put("action", "stop") }, appString(R.string.common_stopped)) }, modifier = Modifier.weight(1f)) { ButtonLabel(appString(R.string.common_stop)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { act(buildJsonObject { put("action", "shuffle"); put("shuffle", "toggle") }, appString(R.string.adapted_plugins_shuffle_toggled)) },
                modifier = Modifier.weight(1f),
            ) { Text(if (now?.shuffle == true) stringResource(R.string.adapted_plugins_shuffle) else stringResource(R.string.adapted_plugins_shuffle_off)) }
            OutlinedButton(onClick = { act(buildJsonObject { put("action", "reload") }, appString(R.string.adapted_plugins_rescanned)) }, modifier = Modifier.weight(1f)) { ButtonLabel(appString(R.string.adapted_plugins_scan_local)) }
            OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.common_refresh)) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = fetchUrl,
            onValueChange = { fetchUrl = it },
            label = { Text(stringResource(R.string.adapted_plugins_cloud_playlist_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().keepAboveIme(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                act(buildJsonObject {
                    put("action", "fetch")
                    put("url", fetchUrl.trim())
                }, appString(R.string.adapted_plugins_fetching_playlist))
            },
            enabled = fetchUrl.isNotBlank(),
        ) { Text(stringResource(R.string.adapted_plugins_fetch_playlist)) }
        val songs = now?.playlist.orEmpty()
        if (songs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.adapted_plugins_playlist, songs.size), style = MaterialTheme.typography.titleSmall)
            songs.forEach { song -> SongRow(song) { act(buildJsonObject { put("action", "play"); put("index", song.index) }, appString(R.string.adapted_plugins_playing_2)) } }
        }
    }
}

@Composable
private fun SongRow(song: SlPlayerSong, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.display, style = MaterialTheme.typography.bodyMedium, fontWeight = if (song.current) FontWeight.SemiBold else FontWeight.Normal)
            Text(formatClock(song.durationSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onPlay) { Text(if (song.current) stringResource(R.string.adapted_plugins_now_playing) else stringResource(R.string.adapted_plugins_play)) }
    }
}

@Composable
private fun OmegaPanel(snapshot: OmegaWarheadSnapshot?) {
    SectionCard("OmegaWarhead", subtitle = stringResource(R.string.adapted_plugins_status_only_neither_2_6)) {
        if (snapshot == null || !snapshot.present) {
            Text(stringResource(R.string.adapted_plugins_plugin_isnt_loaded_monitoring_round), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        KeyValueRow(stringResource(R.string.common_phase), omegaPhaseLabel(snapshot.phase))
        KeyValueRow(
            stringResource(R.string.adapted_plugins_controller),
            snapshot.controllerHolder ?: stringResource(R.string.adapted_plugins_not_held),
            modifier = Modifier.clarityMask(),
        )
        snapshot.countdown?.let { KeyValueRow(stringResource(R.string.adapted_plugins_countdown), "${it}s") }
        if (snapshot.coinHolders.isEmpty()) {
            Text(stringResource(R.string.adapted_plugins_nobody_holding_radioactive_material), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.height(6.dp))
            snapshot.coinHolders.forEach { holder ->
                KeyValueRow(
                    holder.nickname,
                    "${holder.count} · ${holder.position}",
                    modifier = Modifier.clarityMask(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.adapted_plugins_control_plane_only_implements_control),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdaptedRoutesCard(plugin: AdaptedPlugin, actionsEnabled: Boolean) {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf<String?>(null) }
    SectionCard(appString(R.string.adapted_plugins_read_only_routes), subtitle = stringResource(R.string.adapted_plugins_get_plugins_route_using_verifytoken, plugin.id)) {
        Text(
            if (actionsEnabled) {
                stringResource(R.string.adapted_plugins_these_routes_are_registered_by)
            } else {
                stringResource(R.string.adapted_plugins_these_routes_are_registered_by_2)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        plugin.routes.forEach { route ->
            OutlinedButton(
                onClick = {
                    val target = server ?: return@OutlinedButton
                    scope.launch {
                        output = when (val result = http.getRaw(target, "/plugins/${plugin.id}/$route")) {
                            is SlHttpClient.RawHttpResult.Success -> pretty(result.body)
                            is SlHttpClient.RawHttpResult.Failure -> result.message
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 6.dp),
            ) { Text(stringResource(R.string.adapted_plugins_read, route)) }
        }
        output?.let {
            Text(
                it,
                modifier = Modifier.clarityMask(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun AdaptedActionsCard(plugin: AdaptedPlugin) {
    SectionCard(
        stringResource(R.string.adapted_plugins_write_actions),
        subtitle = stringResource(R.string.adapted_plugins_post_control_adapted_action_requires, plugin.id),
    ) {
        Text(
            stringResource(R.string.adapted_plugins_these_actions_change_state_server),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        plugin.actions.forEach { action ->
            AdaptedActionRow(plugin.id, action)
        }
    }
}

@Composable
private fun AdaptedActionRow(pluginId: String, action: String) {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    var body by remember(pluginId, action) { mutableStateOf("") }
    var confirm by remember(pluginId, action) { mutableStateOf(false) }
    var running by remember(pluginId, action) { mutableStateOf(false) }
    var result by remember(pluginId, action) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(action, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(stringResource(R.string.adapted_plugins_request_body_optional_json)) },
            modifier = Modifier.fillMaxWidth().keepAboveIme(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { confirm = true },
            enabled = !running && server?.hasControl == true,
        ) { Text(if (running) stringResource(R.string.adapted_plugins_running) else stringResource(R.string.common_run)) }
        if (server?.hasControl != true) {
            Text(
                stringResource(R.string.adapted_plugins_api_key_required_run_actions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 6.dp).clarityMask(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.adapted_plugins_run, action),
            text = stringResource(R.string.adapted_plugins_write_action_will_recorded_control),
            confirmLabel = stringResource(R.string.common_run),
            onConfirm = {
                val target = server ?: return@ConfirmDialog
                val parsed = body.trim()
                val payload = if (parsed.isEmpty()) {
                    buildJsonObject { }
                } else {
                    val element = runCatching { AppJson.json.parseToJsonElement(parsed) }.getOrNull()
                    if (element !is JsonObject) {
                        result = appString(R.string.adapted_plugins_request_body_must_json_object)
                        return@ConfirmDialog
                    }
                    element
                }
                running = true
                result = null
                scope.launch {
                    result = when (val outcome = ControlRepository.call(target, "/control/adapted/$pluginId/$action", payload)) {
                        is ControlRepository.ControlOutcome.Success ->
                            outcome.data?.toString() ?: outcome.message ?: appString(R.string.adapted_plugins_done)
                        is ControlRepository.ControlOutcome.Failure -> BetaHints.actionFailure(outcome)
                    }
                    running = false
                }
            },
            onDismiss = { confirm = false },
        )
    }
}

private fun pluginKind(plugin: AdaptedPlugin, beta: com.dntof.slconsole.data.model.ControlBeta): String = when {
    plugin.id.equals(SL_PLAYER_ID, true) -> appString(R.string.adapted_plugins_controllable)
    plugin.id.equals(OMEGA_ID, true) -> appString(R.string.adapted_plugins_status_only)
    beta.adaptedActions && plugin.actions.isNotEmpty() -> appString(R.string.adapted_plugins_has_actions)
    plugin.routes.isNotEmpty() -> appString(R.string.adapted_plugins_read_only_routes)
    else -> appString(R.string.adapted_plugins_registered_only)
}

private fun omegaPhaseLabel(phase: String): String = when (phase) {
    "none" -> appString(R.string.common_not_started)
    "collecting" -> appString(R.string.adapted_plugins_collecting_radioactive_material)
    "idle_holding" -> appString(R.string.adapted_plugins_holding_controller)
    "confirming" -> appString(R.string.adapted_plugins_confirming)
    "locked" -> appString(R.string.adapted_plugins_locked)
    "counting" -> appString(R.string.adapted_plugins_counting_down)
    "detonation" -> appString(R.string.adapted_plugins_detonation)
    else -> phase
}

private fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}

private fun pretty(element: JsonElement): String = when (element) {
    is JsonObject, is JsonArray -> element.toString()
    else -> element.toString()
}
