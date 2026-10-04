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
        EmptyState(Icons.Outlined.Extension, "还没有监控数据", "连上服务器后,这里会列出 SLDataAPI 注册的适配插件")
        return
    }
    if (plugins.isEmpty()) {
        EmptyState(
            Icons.Outlined.Extension,
            "没有适配插件",
            "SLPlayer、OmegaWarhead 以及其他通过 PluginEndpointRegistry 注册的插件会出现在这里",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("适配插件", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "数据来自 get_sl_data.adapted_plugins。SLPlayer 可控制播放;OmegaWarhead 目前只有状态。",
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
                        plugin.id.ifBlank { "未提供 id" },
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
                SectionCard("尚未出现在监控快照里") {
                    Text(
                        "等下一轮 get_sl_data。若插件未加载,SLDataAPI 不会注册它。",
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
    SectionCard("登记信息", subtitle = "GET /get_sl_data · adapted_plugins") {
        KeyValueRow("版本", plugin.version ?: "-")
        if (plugin.capabilities.isEmpty()) {
            Text("没有声明 capabilities", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    SectionCard("SLPlayer 播放", subtitle = "POST /control/plugins/slplayer") {
        if (server?.hasControl != true) {
            Text(
                "需要 API Key 才能控制。监控里看到的曲目:${snapshot ?: "未在播放"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        val now = status
        Text(now?.song ?: snapshot ?: "未在播放", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            buildString {
                append(if (now?.playing == true) "播放中" else "已停止")
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
        Text("音量 ${volume.toInt()}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = {
                act(buildJsonObject {
                    put("action", "volume")
                    put("volume", volume.toInt())
                }, "音量已更新")
            },
            valueRange = 0f..100f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { act(buildJsonObject { put("action", "next") }, "下一首") }, modifier = Modifier.weight(1f)) { Text("下一首") }
            OutlinedButton(onClick = { act(buildJsonObject { put("action", "stop") }, "已停止") }, modifier = Modifier.weight(1f)) { Text("停止") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { act(buildJsonObject { put("action", "shuffle"); put("shuffle", "toggle") }, "已切换随机") },
                modifier = Modifier.weight(1f),
            ) { Text(if (now?.shuffle == true) "随机:开" else "随机:关") }
            OutlinedButton(onClick = { act(buildJsonObject { put("action", "reload") }, "已重新扫描") }, modifier = Modifier.weight(1f)) { Text("扫描本地") }
            OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("刷新") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = fetchUrl,
            onValueChange = { fetchUrl = it },
            label = { Text("云端歌单 URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().keepAboveIme(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                act(buildJsonObject {
                    put("action", "fetch")
                    put("url", fetchUrl.trim())
                }, "正在拉取歌单")
            },
            enabled = fetchUrl.isNotBlank(),
        ) { Text("拉取歌单") }
        val songs = now?.playlist.orEmpty()
        if (songs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("播放列表 ${songs.size}", style = MaterialTheme.typography.titleSmall)
            songs.forEach { song -> SongRow(song) { act(buildJsonObject { put("action", "play"); put("index", song.index) }, "已播放") } }
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
        OutlinedButton(onClick = onPlay) { Text(if (song.current) "正在播放" else "播放") }
    }
}

@Composable
private fun OmegaPanel(snapshot: OmegaWarheadSnapshot?) {
    SectionCard("OmegaWarhead", subtitle = "仅状态。2.6.0 和 2.6.1 都没有给它单独的控制端点") {
        if (snapshot == null || !snapshot.present) {
            Text("插件未加载,或这一轮监控还没有 omega_warhead 字段。", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        KeyValueRow("阶段", omegaPhaseLabel(snapshot.phase))
        KeyValueRow(
            "控制器",
            snapshot.controllerHolder ?: "无人持有",
            modifier = Modifier.clarityMask(),
        )
        snapshot.countdown?.let { KeyValueRow("倒计时", "${it}s") }
        if (snapshot.coinHolders.isEmpty()) {
            Text("没有人持有放射性元素", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            "控制面只实现了 /control/plugins/slplayer。OmegaWarhead 的引爆、锁定等操作没有对应 HTTP/WS 端点,这里不猜测调用。",
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
    SectionCard("只读路由", subtitle = "GET /plugins/${plugin.id}/<route>,使用 VerifyToken") {
        Text(
            if (actionsEnabled) {
                "这些路由由插件自己注册，只读。写操作在上面的动作里，每次都会记入控制审计。"
            } else {
                "这些路由由插件自己注册，只读。服务器没在 hello 里声明 adapted_actions 时，这里不提供写操作。"
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
            ) { Text("读取 $route") }
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
        "写操作",
        subtitle = "POST /control/adapted/${plugin.id}/<action>，需要 API Key。2.6.1 内测，默认关闭。",
    ) {
        Text(
            "这些动作会改服务器上的状态，并且写入控制审计。请求体可以留空。",
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
            label = { Text("请求体（可选，JSON）") },
            modifier = Modifier.fillMaxWidth().keepAboveIme(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { confirm = true },
            enabled = !running && server?.hasControl == true,
        ) { Text(if (running) "执行中…" else "执行") }
        if (server?.hasControl != true) {
            Text(
                "需要 API Key 才能执行。",
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
            title = "执行 $action？",
            text = "这是写操作，会记入控制审计。",
            confirmLabel = "执行",
            onConfirm = {
                val target = server ?: return@ConfirmDialog
                val parsed = body.trim()
                val payload = if (parsed.isEmpty()) {
                    buildJsonObject { }
                } else {
                    val element = runCatching { AppJson.json.parseToJsonElement(parsed) }.getOrNull()
                    if (element !is JsonObject) {
                        result = "请求体需要是 JSON 对象"
                        return@ConfirmDialog
                    }
                    element
                }
                running = true
                result = null
                scope.launch {
                    result = when (val outcome = ControlRepository.call(target, "/control/adapted/$pluginId/$action", payload)) {
                        is ControlRepository.ControlOutcome.Success ->
                            outcome.data?.toString() ?: outcome.message ?: "已执行"
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
    plugin.id.equals(SL_PLAYER_ID, true) -> "可控制"
    plugin.id.equals(OMEGA_ID, true) -> "仅状态"
    beta.adaptedActions && plugin.actions.isNotEmpty() -> "可执行动作"
    plugin.routes.isNotEmpty() -> "只读路由"
    else -> "仅登记"
}

private fun omegaPhaseLabel(phase: String): String = when (phase) {
    "none" -> "未开始"
    "collecting" -> "收集放射性元素"
    "idle_holding" -> "持有控制器"
    "confirming" -> "确认中"
    "locked" -> "已锁定"
    "counting" -> "倒计时"
    "detonation" -> "引爆"
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
