// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import com.dntof.slconsole.ui.scrollUnderChrome
import com.microsoft.clarity.modifiers.clarityMask
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.PlayerInfo
import com.dntof.slconsole.data.remote.VoiceClient
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.StatusDot
import com.dntof.slconsole.ui.components.UiColors
import com.dntof.slconsole.ui.rememberActiveServer
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R

@Composable
fun VoiceScreen() {
    val server = rememberActiveServer()
    val monitor by MonitorEngine.state.collectAsState()
    val readyData = (monitor as? MonitorEngine.MonitorState.Ready)?.data
    // 端口优先级:服务器配置的 voicePort > 0 时固定使用;否则从监控 voice_port 自动读取。
    // 自动模式下首次读到的端口锁定使用:监控轮询抖动不应打断已建立的语音连接。
    val configuredPort = server?.voicePort ?: 0
    var voicePortSaved by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(readyData?.voicePort) {
        val live = readyData?.voicePort ?: 0
        if (live > 0 && voicePortSaved == 0) voicePortSaved = live
    }
    val voicePort = if (configuredPort > 0) configuredPort else voicePortSaved
    val enabled = voicePort > 0 && server?.hasControl == true

    if (server == null) {
        EmptyState(Icons.Outlined.GraphicEq, stringResource(R.string.common_no_server_selected), stringResource(R.string.common_add_select_server_top_bar))
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .scrollUnderChrome(),
    ) {
        SectionCard(
            stringResource(R.string.common_voice_monitor),
            subtitle = stringResource(R.string.voice_live_voice_relay_sldataapi_voice),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!server.hasControl) {
                Text(
                    stringResource(R.string.voice_voice_stream_authenticates_api_key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (voicePort <= 0) {
                Text(
                    stringResource(R.string.voice_no_voice_port_yet_make),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val source = if (configuredPort > 0) stringResource(R.string.voice_server_config) else stringResource(R.string.voice_auto_from_monitoring)
                Text(
                    stringResource(R.string.voice_listening_voice_stream_port_mostly, server.addressText, voicePort, source),
                    modifier = Modifier.clarityMask(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (enabled) {
            VoicePanel(server, voicePort, readyData?.players ?: emptyList())
        }
    }
}

@Composable
private fun VoicePanel(server: com.dntof.slconsole.data.model.ServerConfig, voicePort: Int, players: List<PlayerInfo>) {
    val client = remember(server.id, server.host, voicePort, server.apiKey, server.certFingerprint, server.tlsSeen) {
        VoiceClient(server, voicePort)
    }
    DisposableEffect(client) {
        onDispose { client.stop() }
    }

    val state by client.state.collectAsState()
    val detail by client.stateDetail.collectAsState()
    val speakers by client.speakers.collectAsState()
    val error by client.error.collectAsState()
    val muted by client.muted.collectAsState()

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        when (state) {
                            VoiceClient.State.CONNECTED -> UiColors.Online
                            VoiceClient.State.CONNECTING -> UiColors.Pending
                            VoiceClient.State.FAILED -> UiColors.Offline
                            VoiceClient.State.IDLE, VoiceClient.State.CLOSED -> UiColors.Unknown
                        },
                        12.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (state) {
                                VoiceClient.State.CONNECTED -> stringResource(R.string.voice_voice_stream_connected)
                                VoiceClient.State.CONNECTING -> stringResource(R.string.common_connecting)
                                VoiceClient.State.FAILED -> stringResource(R.string.common_connection_failed)
                                VoiceClient.State.IDLE -> stringResource(R.string.voice_not_listening)
                                VoiceClient.State.CLOSED -> stringResource(R.string.voice_disconnected)
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            detail,
                            modifier = Modifier.clarityMask(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { client.setMuted(!muted) }) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (muted) stringResource(R.string.voice_unmute) else stringResource(R.string.voice_mute),
                        )
                    }
                }
                error?.let {
                    Text(
                        "✗ $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { client.start() },
                        enabled = state == VoiceClient.State.IDLE || state == VoiceClient.State.FAILED || state == VoiceClient.State.CLOSED,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.voice_start_listening)) }
                    OutlinedButton(
                        onClick = { client.stop() },
                        enabled = state == VoiceClient.State.CONNECTED || state == VoiceClient.State.CONNECTING,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.common_stop)) }
                }
            }
        }

        SectionCard(stringResource(R.string.voice_speaking_now), subtitle = stringResource(R.string.voice_speakers_no_new_audio_1)) {
            if (speakers.isEmpty()) {
                Text(
                    stringResource(R.string.voice_tap_start_listening_connect_server),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                speakers.forEach { speaker ->
                    val liveRole = players.find { it.steamId == speaker.steamId }?.role
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clarityMask(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                speaker.nickname.firstOrNull()?.toString() ?: "?",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(speaker.nickname, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                liveRole ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        speaker.channels.forEach { channel ->
                            InfoChip(
                                when (channel) {
                                    0 -> stringResource(R.string.voice_proximity)
                                    1 -> stringResource(R.string.voice_walkie_talkie)
                                    2 -> stringResource(R.string.voice_scp_chat)
                                    3 -> stringResource(R.string.voice_radio)
                                    else -> stringResource(R.string.voice_channel, channel)
                                },
                                MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            }
        }
    }
}
