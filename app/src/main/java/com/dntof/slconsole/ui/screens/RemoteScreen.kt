package com.dntof.slconsole.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.LabeledSwitch
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.rememberActiveServer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dntof.slconsole.ui.withBottomChrome

/** 远程控制:广播 / 管理聊天 / CASSIE / 回合与核弹(命令执行在"控制台"标签页)。 */
@Composable
fun RemoteScreen() {
    val server = rememberActiveServer()
    if (server == null) {
        EmptyState(Icons.Outlined.Terminal, "未选择服务器", "先在顶栏添加或选择一个服务器")
        return
    }

    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var broadcastText by rememberSaveable { mutableStateOf("") }
    var broadcastDuration by remember { mutableFloatStateOf(5f) }
    var clearPrevious by rememberSaveable { mutableStateOf(false) }

    var staffText by rememberSaveable { mutableStateOf("") }
    var staffSilent by rememberSaveable { mutableStateOf(false) }

    var cassieText by rememberSaveable { mutableStateOf("") }
    var cassieTranslation by rememberSaveable { mutableStateOf("") }
    var cassieHeld by rememberSaveable { mutableStateOf(false) }
    var cassieNoisy by rememberSaveable { mutableStateOf(true) }
    var cassieSubs by rememberSaveable { mutableStateOf(true) }

    var pendingRound by remember { mutableStateOf<String?>(null) }
    var pendingWarhead by remember { mutableStateOf<String?>(null) }

    fun send(path: String, body: kotlinx.serialization.json.JsonObject, okText: String) {
        scope.launch {
            snackbar.showOutcome(ControlRepository.call(server, path, body), okText)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("全服广播", subtitle = "显示在所有玩家屏幕中央,内容 ≤500 字符") {
                OutlinedTextField(
                    value = broadcastText,
                    onValueChange = { if (it.length <= 500) broadcastText = it },
                    label = { Text("广播内容") },
                    minLines = 2,
                    supportingText = { Text("${broadcastText.length}/500") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "显示时长:${broadcastDuration.toInt()} 秒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = broadcastDuration,
                    onValueChange = { broadcastDuration = it },
                    valueRange = 1f..60f,
                )
                LabeledSwitch("清除上一个广播", null, clearPrevious) { clearPrevious = it }
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = broadcastText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        send(
                            "/control/broadcast",
                            buildJsonObject {
                                put("message", broadcastText.trim())
                                put("duration_seconds", broadcastDuration)
                                put("clear_previous", clearPrevious)
                            },
                            "广播已发送",
                        )
                    },
                ) { Text("发送广播") }
            }
        }
        item {
            SectionCard("管理聊天", subtitle = "仅对拥有 AdminChat 权限的玩家可见") {
                OutlinedTextField(
                    value = staffText,
                    onValueChange = { if (it.length <= 500) staffText = it },
                    label = { Text("管理聊天消息") },
                    minLines = 2,
                    supportingText = { Text("${staffText.length}/500") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledSwitch("静默发送", "不显示发送者", staffSilent) { staffSilent = it }
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = staffText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        send(
                            "/control/staffchat",
                            buildJsonObject {
                                put("message", staffText.trim())
                                put("is_silent", staffSilent)
                            },
                            "已发送到管理聊天",
                        )
                    },
                ) { Text("发送管理聊天") }
            }
        }
        item {
            SectionCard("CASSIE 播报", subtitle = "播报使用英文大写单词;字幕文本用于中文翻译") {
                OutlinedTextField(
                    value = cassieText,
                    onValueChange = { if (it.length <= 500) cassieText = it },
                    label = { Text("播报文本(英文)") },
                    minLines = 2,
                    supportingText = { Text("${cassieText.length}/500") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = cassieTranslation,
                    onValueChange = { if (it.length <= 500) cassieTranslation = it },
                    label = { Text("字幕翻译(可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledSwitch("保持播报(等待确认)", null, cassieHeld) { cassieHeld = it }
                LabeledSwitch("包含环境音", null, cassieNoisy) { cassieNoisy = it }
                LabeledSwitch("显示字幕", null, cassieSubs) { cassieSubs = it }
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = cassieText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        send(
                            "/control/cassie",
                            buildJsonObject {
                                put("message", cassieText.trim())
                                put("isHeld", cassieHeld)
                                put("isNoisy", cassieNoisy)
                                put("isSubtitles", cassieSubs)
                                if (cassieTranslation.isNotBlank()) put("translation", cassieTranslation.trim())
                            },
                            "CASSIE 播报已发送",
                        )
                    },
                ) { Text("发送 CASSIE") }
            }
        }
        item {
            SectionCard("回合与核弹", subtitle = "危险操作均需二次确认") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingRound = "restart" }, modifier = Modifier.weight(1f)) { Text("重启回合") }
                    OutlinedButton(onClick = { pendingRound = "end" }, modifier = Modifier.weight(1f)) { Text("结束回合") }
                    OutlinedButton(onClick = { pendingRound = "start" }, modifier = Modifier.weight(1f)) { Text("开始回合") }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingWarhead = "start" }, modifier = Modifier.weight(1f)) { Text("启动核弹") }
                    OutlinedButton(onClick = { pendingWarhead = "stop" }, modifier = Modifier.weight(1f)) { Text("停止") }
                    Button(onClick = { pendingWarhead = "detonate" }, modifier = Modifier.weight(1f)) { Text("引爆") }
                }
            }
        }
    }

    pendingRound?.let { action ->
        val label = when (action) {
            "restart" -> "重启"
            "end" -> "结束"
            else -> "开始"
        }
        ConfirmDialog(
            title = "确认${label}当前回合?",
            danger = action != "start",
            confirmLabel = label,
            onConfirm = {
                send("/control/round", buildJsonObject { put("action", action) }, "回合已${label}")
            },
            onDismiss = { pendingRound = null },
        )
    }
    pendingWarhead?.let { action ->
        val label = when (action) {
            "start" -> "启动"
            "stop" -> "停止"
            else -> "引爆"
        }
        ConfirmDialog(
            title = "确认${label}核弹?",
            text = if (action == "detonate") "核弹将立即引爆,无法撤回。" else null,
            danger = action == "detonate",
            confirmLabel = label,
            onConfirm = {
                send("/control/round/warhead", buildJsonObject { put("action", action) }, "核弹指令已执行")
            },
            onDismiss = { pendingWarhead = null },
        )
    }
}
