package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.PlayerDetailData
import com.dntof.slconsole.data.model.PlayerInfo
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.withBottomChrome
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.components.PromptDialog
import com.dntof.slconsole.ui.components.PromptField
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.components.teamColor
import com.dntof.slconsole.ui.rememberActiveServer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val ROLE_PRESETS = listOf(
    "ClassD", "Scientist", "FacilityGuard",
    "NtfPrivate", "NtfSpecialist", "NtfSergeant", "NtfCaptain",
    "ChaosConscript", "ChaosRifleman", "ChaosRepressor", "ChaosMarauder",
    "Scp173", "Scp106", "Scp049", "Scp096", "Scp939", "Scp3114", "Scp0492",
    "Tutorial", "Spectator",
)

@Composable
fun PlayersScreen() {
    val monitorState by MonitorEngine.state.collectAsState()
    val server = rememberActiveServer()
    val players = (monitorState as? MonitorEngine.MonitorState.Ready)?.data?.players ?: emptyList()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<PlayerInfo?>(null) }

    val filtered = remember(players, query) {
        if (query.isBlank()) players
        else players.filter { it.nickname.contains(query, true) || it.steamId.contains(query, true) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("玩家", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    players.isEmpty() -> "等待玩家加入"
                    query.isBlank() -> "在线 ${players.size}"
                    else -> "在线 ${players.size} · 匹配 ${filtered.size}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索昵称 / SteamID") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Outlined.Groups,
                if (players.isEmpty()) "暂无在线玩家" else "没有匹配的玩家",
                "玩家加入后自动出现在这里",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp).withBottomChrome(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.steamId + it.nickname }) { player ->
                    PlayerRow(player, onClick = { selected = player })
                }
            }
        }
    }

    selected?.let { player ->
        PlayerActionsSheet(player, server, onDismiss = { selected = null })
    }
}

@Composable
private fun PlayerRow(player: PlayerInfo, onClick: () -> Unit) {
    AppSurface(Modifier.fillMaxWidth(), onClick = onClick, role = GlassRole.Row) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    player.nickname.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(player.nickname, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    player.steamId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            InfoChip(player.team.ifBlank { "未知" }, teamColor(player.team))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerActionsSheet(player: PlayerInfo, server: ServerConfig?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var detail by remember { mutableStateOf<PlayerDetailData?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var muteAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(player.steamId, server?.id) {
        if (server?.hasControl == true) {
            val outcome = ControlRepository.call(
                server,
                "/control/player/data",
                buildJsonObject { put("target", player.steamId) },
            )
            when (outcome) {
                is ControlRepository.ControlOutcome.Success ->
                    detail = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(PlayerDetailData.serializer(), it) }.getOrNull()
                    }
                is ControlRepository.ControlOutcome.Failure -> detailError = outcome.message
            }
        }
    }

    fun callControl(path: String, body: JsonObject, okText: String) {
        val target = server ?: return
        scope.launch {
            snackbar.showOutcome(ControlRepository.call(target, path, body), okText)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        player.nickname.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(player.nickname, style = MaterialTheme.typography.titleMedium)
                    Text(
                        player.steamId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                InfoChip(player.role.ifBlank { "未知角色" }, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                InfoChip(player.team.ifBlank { "未知" }, teamColor(player.team))
            }

            if (server?.hasControl != true) {
                Spacer(Modifier.height(16.dp))
                AppSurface {
                    Text(
                        "在服务器设置中配置 API Key 后,即可使用玩家管理操作。",
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                SectionCard("玩家详情") {
                    if (detail != null) {
                        KeyValueRow("UserID", detail?.userid ?: "-", mono = true)
                        KeyValueRow("PlayerId", detail?.playerId?.toString() ?: "-")
                        KeyValueRow("血量", detail?.health?.let { "%.0f".format(it) } ?: "-")
                        KeyValueRow("房间", detail?.room ?: "-")
                        detail?.position?.let { pos ->
                            KeyValueRow(
                                "坐标",
                                listOfNotNull(pos.x, pos.y, pos.z).joinToString(", ") { "%.0f".format(it) },
                                mono = true,
                            )
                        }
                    } else {
                        Text(
                            detailError ?: "读取中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionCard("管理操作") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { dialog = "msg" }, modifier = Modifier.weight(1f)) { Text("私信") }
                        TextButton(onClick = { dialog = "kick" }, modifier = Modifier.weight(1f)) { Text("踢出") }
                        TextButton(onClick = { dialog = "ban" }, modifier = Modifier.weight(1f)) { Text("封禁") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { muteAction = "mute" }, modifier = Modifier.weight(1f)) { Text("禁言") }
                        TextButton(onClick = { muteAction = "unmute" }, modifier = Modifier.weight(1f)) { Text("解除禁言") }
                        TextButton(onClick = { dialog = "role" }, modifier = Modifier.weight(1f)) { Text("换角色") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { dialog = "effect" }, modifier = Modifier.weight(1f)) { Text("效果") }
                        TextButton(onClick = { dialog = "teleport" }, modifier = Modifier.weight(1f)) { Text("传送") }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    when (dialog) {
        "msg" -> PromptDialog(
            title = "私信 ${player.nickname}",
            subtitle = "以提示(hint)形式显示在目标玩家屏幕上",
            confirmLabel = "发送",
            fields = listOf(
                PromptField("message", "消息内容"),
                PromptField("duration", "显示秒数(1-60)", initial = "5", keyboardType = KeyboardType.Number),
            ),
            onConfirm = { v ->
                val message = v["message"].orEmpty().trim()
                if (message.isNotEmpty()) {
                    callControl(
                        "/control/moderation/msg",
                        buildJsonObject {
                            put("target", player.steamId)
                            put("message", message)
                            put("msg_type", "hint")
                            put("duration_seconds", v["duration"]?.toFloatOrNull() ?: 5f)
                        },
                        "已发送",
                    )
                }
            },
            onDismiss = { dialog = null },
        )
        "kick" -> PromptDialog(
            title = "踢出 ${player.nickname}",
            confirmLabel = "踢出",
            danger = true,
            fields = listOf(PromptField("reason", "原因(可选)")),
            onConfirm = { v ->
                callControl(
                    "/control/moderation/kick",
                    buildJsonObject {
                        put("target", player.steamId)
                        put("reason", v["reason"].orEmpty().ifBlank { "由管理端移出" })
                    },
                    "已踢出",
                )
            },
            onDismiss = { dialog = null },
        )
        "ban" -> PromptDialog(
            title = "封禁 ${player.nickname}",
            subtitle = "时长为分钟数,0 表示永久封禁",
            confirmLabel = "封禁",
            danger = true,
            fields = listOf(
                PromptField("reason", "原因"),
                PromptField("duration", "时长(分钟,0=永久)", initial = "0", keyboardType = KeyboardType.Number),
            ),
            onConfirm = { v ->
                callControl(
                    "/control/moderation/ban",
                    buildJsonObject {
                        put("target", player.steamId)
                        put("reason", v["reason"].orEmpty().ifBlank { "违规行为" })
                        put("duration", v["duration"]?.toIntOrNull() ?: 0)
                    },
                    "已封禁",
                )
            },
            onDismiss = { dialog = null },
        )
        "role" -> RoleDialog(
            onConfirm = { role ->
                callControl(
                    "/control/player/role",
                    buildJsonObject {
                        put("target", player.steamId)
                        put("role", role)
                    },
                    "角色已设置为 $role",
                )
            },
            onDismiss = { dialog = null },
        )
        "effect" -> PromptDialog(
            title = "施加效果",
            subtitle = "效果为 EffectType 名称,如 Flashed / Blurred / Burned",
            fields = listOf(
                PromptField("effect", "效果名", initial = "Flashed"),
                PromptField("duration", "持续秒数", initial = "10", keyboardType = KeyboardType.Number),
            ),
            onConfirm = { v ->
                val effect = v["effect"].orEmpty().trim()
                if (effect.isNotEmpty()) {
                    callControl(
                        "/control/player/effects",
                        buildJsonObject {
                            put("target", player.steamId)
                            put("effect", effect)
                            put("effect_duration", v["duration"]?.toFloatOrNull() ?: 10f)
                        },
                        "效果已施加",
                    )
                }
            },
            onDismiss = { dialog = null },
        )
        "teleport" -> PromptDialog(
            title = "传送玩家",
            subtitle = "输入目标世界坐标",
            fields = listOf(
                PromptField("x", "X", keyboardType = KeyboardType.Decimal),
                PromptField("y", "Y", keyboardType = KeyboardType.Decimal),
                PromptField("z", "Z", keyboardType = KeyboardType.Decimal),
            ),
            onConfirm = { v ->
                val x = v["x"]?.toFloatOrNull()
                val y = v["y"]?.toFloatOrNull()
                val z = v["z"]?.toFloatOrNull()
                if (x != null && y != null && z != null) {
                    callControl(
                        "/control/admin/teleport",
                        buildJsonObject {
                            put("target", player.steamId)
                            put("x", x)
                            put("y", y)
                            put("z", z)
                        },
                        "已传送",
                    )
                }
            },
            onDismiss = { dialog = null },
        )
    }

    muteAction?.let { action ->
        val muting = action == "mute"
        ConfirmDialog(
            title = if (muting) "禁言 ${player.nickname}?" else "解除 ${player.nickname} 的禁言?",
            text = "作用于语音频道。",
            confirmLabel = if (muting) "禁言" else "解除",
            danger = muting,
            onConfirm = {
                callControl(
                    "/control/moderation/mute",
                    buildJsonObject {
                        put("target", player.steamId)
                        put("mute", muting)
                        put("mute_scope", "voice")
                    },
                    if (muting) "已禁言" else "已解除禁言",
                )
            },
            onDismiss = { muteAction = null },
        )
    }
}

@Composable
private fun RoleDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var role by remember { mutableStateOf("ClassD") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置角色") },
        text = {
            Column {
                Text(
                    "填入 RoleTypeId 枚举名,或从常用列表选择。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("RoleTypeId") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, "选择常用角色")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ROLE_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset, fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    role = preset
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (role.isNotBlank()) onConfirm(role.trim())
                onDismiss()
            }) { Text("设置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
