// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
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
import androidx.compose.material3.BottomSheetDefaults
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
import com.microsoft.clarity.modifiers.clarityMask
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
import com.dntof.slconsole.ui.LocalImeLift
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.ui.withBottomChrome
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.components.ImeDialogFrame
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
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.common_players), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    players.isEmpty() -> stringResource(R.string.players_waiting_players_join)
                    query.isBlank() -> stringResource(R.string.players_online, players.size)
                    else -> stringResource(R.string.players_online_matched, players.size, filtered.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.players_search_nickname_steamid)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.Groups,
                    if (players.isEmpty()) stringResource(R.string.players_no_players_online) else stringResource(R.string.players_no_matching_players),
                    stringResource(R.string.players_players_appear_here_automatically_when),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
            items(filtered, key = { it.steamId + it.nickname }) { player ->
                Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(160))) { PlayerRow(player, onClick = { selected = player }) }
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
        Row(Modifier.padding(12.dp).clarityMask(), verticalAlignment = Alignment.CenterVertically) {
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
            InfoChip(player.team.ifBlank { stringResource(R.string.common_unknown) }, teamColor(player.team))
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.clarityMask(),
        sheetState = sheetState,
        contentWindowInsets = {
            BottomSheetDefaults.windowInsets
                .union(WindowInsets.ime)
                .union(WindowInsets(bottom = LocalImeLift.current.overlap))
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.clarityMask(), verticalAlignment = Alignment.CenterVertically) {
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
                InfoChip(player.role.ifBlank { stringResource(R.string.players_unknown_role) }, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                InfoChip(player.team.ifBlank { stringResource(R.string.common_unknown) }, teamColor(player.team))
            }

            if (server?.hasControl != true) {
                Spacer(Modifier.height(16.dp))
                AppSurface {
                    Text(
                        stringResource(R.string.players_configure_api_key_server_settings),
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                SectionCard(stringResource(R.string.players_player_details)) {
                    if (detail != null) {
                        Column(Modifier.clarityMask()) {
                            KeyValueRow("UserID", detail?.userid ?: "-", mono = true)
                            KeyValueRow("PlayerId", detail?.playerId?.toString() ?: "-")
                            KeyValueRow(stringResource(R.string.players_health), detail?.health?.let { "%.0f".format(it) } ?: "-")
                            KeyValueRow(stringResource(R.string.players_room), detail?.room ?: "-")
                            detail?.position?.let { pos ->
                                KeyValueRow(
                                    stringResource(R.string.players_position),
                                    listOfNotNull(pos.x, pos.y, pos.z).joinToString(", ") { "%.0f".format(it) },
                                    mono = true,
                                )
                            }
                        }
                    } else {
                        Text(
                            detailError ?: stringResource(R.string.players_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionCard(stringResource(R.string.players_management)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { dialog = "msg" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.players_message)) }
                        TextButton(onClick = { dialog = "kick" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.players_kick)) }
                        TextButton(onClick = { dialog = "ban" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_ban)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { muteAction = "mute" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.players_mute)) }
                        TextButton(onClick = { muteAction = "unmute" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.players_unmute)) }
                        TextButton(onClick = { dialog = "role" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.players_set_role)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { dialog = "effect" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.players_effect)) }
                        TextButton(onClick = { dialog = "teleport" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.players_teleport)) }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    when (dialog) {
        "msg" -> PromptDialog(
            title = stringResource(R.string.players_message_2, player.nickname),
            subtitle = stringResource(R.string.players_shown_as_hint_target_players),
            confirmLabel = stringResource(R.string.common_send),
            fields = listOf(
                PromptField("message", stringResource(R.string.players_message_3)),
                PromptField("duration", stringResource(R.string.common_seconds_display_1_60), initial = "5", keyboardType = KeyboardType.Number),
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
                        appString(R.string.players_sent),
                    )
                }
            },
            onDismiss = { dialog = null },
        )
        "kick" -> PromptDialog(
            title = stringResource(R.string.players_kick_2, player.nickname),
            confirmLabel = stringResource(R.string.players_kick),
            danger = true,
            fields = listOf(PromptField("reason", stringResource(R.string.players_reason_optional))),
            onConfirm = { v ->
                callControl(
                    "/control/moderation/kick",
                    buildJsonObject {
                        put("target", player.steamId)
                        put("reason", v["reason"].orEmpty().ifBlank { "由管理端移出" })
                    },
                    appString(R.string.players_kicked),
                )
            },
            onDismiss = { dialog = null },
        )
        "ban" -> PromptDialog(
            title = stringResource(R.string.players_ban, player.nickname),
            subtitle = stringResource(R.string.players_duration_minutes_0_means_permanent),
            confirmLabel = stringResource(R.string.common_ban),
            danger = true,
            fields = listOf(
                PromptField("reason", stringResource(R.string.common_reason)),
                PromptField("duration", stringResource(R.string.common_duration_minutes_0_permanent), initial = "0", keyboardType = KeyboardType.Number),
            ),
            onConfirm = { v ->
                callControl(
                    "/control/moderation/ban",
                    buildJsonObject {
                        put("target", player.steamId)
                        put("reason", v["reason"].orEmpty().ifBlank { "违规行为" })
                        put("duration", v["duration"]?.toIntOrNull() ?: 0)
                    },
                    appString(R.string.players_banned),
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
                    appString(R.string.players_role_set, role),
                )
            },
            onDismiss = { dialog = null },
        )
        "effect" -> PromptDialog(
            title = stringResource(R.string.players_apply_effect),
            subtitle = stringResource(R.string.players_effect_effecttype_name_such_as),
            fields = listOf(
                PromptField("effect", stringResource(R.string.players_effect_name), initial = "Flashed"),
                PromptField("duration", stringResource(R.string.players_duration_seconds), initial = "10", keyboardType = KeyboardType.Number),
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
                        appString(R.string.players_effect_applied),
                    )
                }
            },
            onDismiss = { dialog = null },
        )
        "teleport" -> PromptDialog(
            title = stringResource(R.string.players_teleport_player),
            subtitle = stringResource(R.string.players_enter_target_world_coordinates),
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
                        appString(R.string.players_teleported),
                    )
                }
            },
            onDismiss = { dialog = null },
        )
    }

    muteAction?.let { action ->
        val muting = action == "mute"
        ConfirmDialog(
            title = if (muting) stringResource(R.string.players_mute_2, player.nickname) else stringResource(R.string.players_unmute_2, player.nickname),
            text = stringResource(R.string.players_applies_voice_chat),
            confirmLabel = if (muting) stringResource(R.string.players_mute) else stringResource(R.string.players_unmute_3),
            danger = muting,
            onConfirm = {
                callControl(
                    "/control/moderation/mute",
                    buildJsonObject {
                        put("target", player.steamId)
                        put("mute", muting)
                        put("mute_scope", "voice")
                    },
                    if (muting) appString(R.string.players_muted) else appString(R.string.players_unmuted),
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
    ImeDialogFrame(onDismiss = onDismiss) {
        Text(stringResource(R.string.players_set_role_2), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.players_enter_roletypeid_enum_name_pick),
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
                        Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.players_pick_common_role))
                    }
                },
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
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
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            TextButton(onClick = {
                if (role.isNotBlank()) onConfirm(role.trim())
                onDismiss()
            }) { Text(stringResource(R.string.players_set)) }
        }
    }
}
