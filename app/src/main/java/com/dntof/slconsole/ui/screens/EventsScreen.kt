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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Elevator
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.SlEvent
import com.dntof.slconsole.data.remote.WsControlClient
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.belowTopBar
import com.microsoft.clarity.modifiers.clarityMask
import com.dntof.slconsole.ui.bottomChromePadding
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.withBottomChrome
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.StatusDot
import com.dntof.slconsole.ui.components.UiColors
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.util.Format
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.resolveCompose

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.content ?: ""

@Composable
fun EventsScreen(onOpenServerEdit: () -> Unit) {
    val server = rememberActiveServer()

    if (server == null) {
        EmptyState(Icons.Outlined.Bolt, stringResource(R.string.common_no_server_selected), stringResource(R.string.common_add_select_server_top_bar))
        return
    }
    if (!server.hasControl || server.controlTransport != "ws") {
        Column(Modifier.fillMaxSize().belowTopBar().padding(16.dp).bottomChromePadding()) {
            SectionCard(stringResource(R.string.events_live_events_unavailable), subtitle = stringResource(R.string.events_following_conditions_are_required)) {
                Text(
                    stringResource(R.string.events_1_api_key_control_plane),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(0.dp))
                Button(onClick = onOpenServerEdit, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.events_go_server_settings))
                }
            }
        }
        return
    }

    val client = remember(server.id, server.host, server.port, server.apiKey, server.certFingerprint, server.tlsSeen) {
        ControlRepository.eventsClient(server)
    }
    DisposableEffect(server.id) {
        client.subscribeEvents()
        onDispose { client.unsubscribeEvents() }
    }

    val connState by client.state.collectAsState()
    val detail by client.stateDetail.collectAsState()
    val handshake by client.handshakeStatus.collectAsState()
    val events by client.events.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().clarityMask(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
        AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        when (connState) {
                            WsControlClient.ConnState.READY -> UiColors.Online
                            WsControlClient.ConnState.CONNECTING -> UiColors.Pending
                            WsControlClient.ConnState.FAILED -> UiColors.Offline
                            WsControlClient.ConnState.CLOSED -> UiColors.Unknown
                        },
                        12.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (connState) {
                                WsControlClient.ConnState.READY -> stringResource(R.string.events_live_connection_ok)
                                WsControlClient.ConnState.CONNECTING -> stringResource(R.string.common_connecting)
                                WsControlClient.ConnState.FAILED -> stringResource(R.string.common_connection_failed)
                                WsControlClient.ConnState.CLOSED -> stringResource(R.string.events_connection_closed)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            detail.resolveCompose(),
                            modifier = Modifier.clarityMask(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { client.clearEvents() }) { Text(stringResource(R.string.events_clear)) }
                }
                if (connState == WsControlClient.ConnState.FAILED && handshake == 404) {
                    Text(
                        stringResource(R.string.events_handshake_rejected_http_404_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        }

        if (events.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.Bolt,
                    stringResource(R.string.events_no_events_yet),
                    stringResource(R.string.events_player_joins_leaves_deaths_round),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
            items(events, key = { it.receivedAt.toString() + it.event }) { event ->
                Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(160))) { EventRow(event) }
            }
        }
    }
}

private data class EventMeta(val icon: ImageVector, val color: Color, val title: String, val subtitle: String)

@Composable
private fun eventMeta(event: SlEvent): EventMeta = when (event.event) {
    "player_joined" -> EventMeta(
        Icons.AutoMirrored.Outlined.Login, Color(0xFF4ADE80),
        stringResource(R.string.events_player_joined), event.data.str("nickname").ifBlank { event.data.str("userid") },
    )
    "player_left" -> EventMeta(
        Icons.AutoMirrored.Outlined.Logout, Color(0xFF9CA3AF),
        stringResource(R.string.events_player_left), event.data.str("nickname").ifBlank { event.data.str("userid") },
    )
    "player_died" -> {
        val attacker = event.data.str("attacker_nickname")
        EventMeta(
            Icons.Outlined.PersonOff, Color(0xFFEF5350),
            stringResource(R.string.events_player_died),
            buildString {
                append(event.data.str("nickname").ifBlank { event.data.str("userid") })
                val oldRole = event.data.str("old_role")
                if (oldRole.isNotBlank()) append("($oldRole)")
                if (attacker.isNotBlank()) append(stringResource(R.string.events_killer, attacker))
            },
        )
    }
    "round_started" -> EventMeta(Icons.Outlined.PlayArrow, Color(0xFF7FD8E8), stringResource(R.string.events_round_started), event.data.str("started_at"))
    "round_ended" -> EventMeta(
        Icons.Outlined.Stop, Color(0xFF60A5FA),
        stringResource(R.string.events_round_ended),
        event.data.str("leading_team").takeIf { it.isNotBlank() }?.let { stringResource(R.string.events_leading_team, it) } ?: "",
    )
    "elevator_used" -> EventMeta(
        Icons.Outlined.Elevator, Color(0xFFFFC46B),
        stringResource(R.string.events_used_elevator),
        listOf(event.data.str("nickname"), event.data.str("elevator_group")).filter { it.isNotBlank() }.joinToString(" · "),
    )
    "door_opened" -> EventMeta(
        Icons.Outlined.MeetingRoom, Color(0xFF9AD48E),
        stringResource(R.string.events_opened_door),
        listOf(event.data.str("nickname"), event.data.str("door")).filter { it.isNotBlank() }.joinToString(" · "),
    )
    "_sys" -> EventMeta(Icons.Outlined.Info, UiColors.Pending, stringResource(R.string.events_system), sysSubtitle(event))
    else -> EventMeta(Icons.Outlined.Bolt, UiColors.Unknown, event.event, "")
}

@Composable
private fun sysSubtitle(event: SlEvent): String {
    val res = event.messageRes
    return if (res != null) {
        if (event.messageArgs.isEmpty()) stringResource(res) else stringResource(res, *event.messageArgs.toTypedArray())
    } else {
        event.data.str("text")
    }
}

@Composable
private fun EventRow(event: SlEvent) {
    val meta = eventMeta(event)
    AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Row) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(meta.color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(meta.icon, null, Modifier.size(20.dp), tint = meta.color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(meta.title, style = MaterialTheme.typography.titleSmall)
                if (meta.subtitle.isNotBlank()) {
                    Text(
                        meta.subtitle,
                        modifier = Modifier.clarityMask(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Text(
                Format.isoTime(event.utc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
