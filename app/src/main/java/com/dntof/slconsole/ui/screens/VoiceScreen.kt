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
        EmptyState(Icons.Outlined.GraphicEq, "未选择服务器", "先在顶栏添加或选择一个服务器")
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .scrollUnderChrome(),
    ) {
        SectionCard(
            "语音监听",
            subtitle = "实时代码语音转发(SLDataAPI voice_enabled)",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!server.hasControl) {
                Text(
                    "语音流使用 API Key 鉴权:请先在服务器设置中配置控制面 API Key。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (voicePort <= 0) {
                Text(
                    "暂未获取到语音端口:确认 SLDataAPI 配置 voice_enabled: true(需重启游戏服生效)," +
                        "或在服务器设置中手动填写语音流端口(服务器 voice_port 非默认值时必填)。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val source = if (configuredPort > 0) "服务器配置" else "监控自动获取"
                Text(
                    "监听 ${server.addressText} 的语音流(端口 $voicePort · $source)。多为近距离/对讲频道混音,请调低音量后开始。",
                    modifier = Modifier.clarityMask(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (enabled) {
            VoicePanel(server.host, voicePort, server.apiKey, readyData?.players ?: emptyList())
        }
    }
}

@Composable
private fun VoicePanel(host: String, voicePort: Int, apiKey: String, players: List<PlayerInfo>) {
    val client = remember(host, voicePort, apiKey) { VoiceClient(host, voicePort, apiKey) }
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
                                VoiceClient.State.CONNECTED -> "语音流已连接"
                                VoiceClient.State.CONNECTING -> "正在连接…"
                                VoiceClient.State.FAILED -> "连接失败"
                                VoiceClient.State.IDLE -> "未开始监听"
                                VoiceClient.State.CLOSED -> "已断开"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { client.setMuted(!muted) }) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (muted) "取消静音" else "静音",
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
                    ) { Text("开始监听") }
                    OutlinedButton(
                        onClick = { client.stop() },
                        enabled = state == VoiceClient.State.CONNECTED || state == VoiceClient.State.CONNECTING,
                        modifier = Modifier.weight(1f),
                    ) { Text("停止") }
                }
            }
        }

        SectionCard("正在说话", subtitle = "1.5 秒无新音频的说话人会自动移出") {
            if (speakers.isEmpty()) {
                Text(
                    "点击「开始监听」连接服务器语音流;有人说话时会显示在这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                speakers.forEach { speaker ->
                    val liveRole = players.find { it.steamId == speaker.steamId }?.role
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                                    0 -> "近距离"
                                    1 -> "对讲机"
                                    2 -> "斯科通"
                                    3 -> "无线电"
                                    else -> "频道 $channel"
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
