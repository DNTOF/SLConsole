// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.SlHttpClient
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.util.stripRichText
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.ui.withBottomChrome

@Composable
fun ServerEditScreen(serverId: String?, onDone: () -> Unit) {
    val store = ServiceLocator.serverStore
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    val existing by produceState<ServerConfig?>(initialValue = null, serverId) {
        if (serverId != null) {
            value = store.serversFlow.map { list -> list.find { it.id == serverId } }.first()
        }
    }
    // 编辑模式:配置首次加载完成后,仅回填一次表单(避免异步加载覆盖用户已输入内容)
    var label by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(ServerConfig.DEFAULT_PORT.toString()) }
    var verifyToken by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var transport by rememberSaveable { mutableStateOf("http") }
    var voicePortText by rememberSaveable { mutableStateOf("") }
    var intervalSec by remember { mutableFloatStateOf((ServerConfig.DEFAULT_INTERVAL_MS / 1000).toFloat()) }
    var prefilled by remember { mutableStateOf(serverId == null) }

    LaunchedEffect(existing) {
        val cfg = existing ?: return@LaunchedEffect
        if (prefilled) return@LaunchedEffect
        prefilled = true
        label = cfg.label
        host = cfg.host
        port = cfg.port.toString()
        verifyToken = cfg.verifyToken
        apiKey = cfg.apiKey
        transport = cfg.controlTransport
        voicePortText = if (cfg.voicePort > 0) cfg.voicePort.toString() else ""
        intervalSec = (cfg.refetchIntervalMs / 1000).toFloat()
    }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun testConnection() {
        val h = host.trim()
        val p = port.toIntOrNull() ?: ServerConfig.DEFAULT_PORT
        if (h.isEmpty() || verifyToken.isBlank()) {
            testResult = false to "请先填写主机地址和 VerifyToken"
            return
        }
        testing = true
        testResult = null
        scope.launch {
            val temp = ServerConfig(id = "test", host = h, port = p, verifyToken = verifyToken)
            when (val result = SlHttpClient().getData(temp, "/get_sl_data")) {
                is SlHttpClient.HttpResult.Success -> {
                    val name = (result.body["server_name"] as? JsonPrimitive)?.content?.let { stripRichText(it) }
                    val count = (result.body["players_count"] as? JsonPrimitive)?.content
                    testResult = true to "连接成功:${name ?: "(未命名服务器)"}(在线 $count 人)"
                }
                is SlHttpClient.HttpResult.Failure -> testResult = false to result.message
            }
            testing = false
        }
    }

    fun save() {
        val h = host.trim()
        val p = port.toIntOrNull()
        when {
            h.isEmpty() -> saveError = "主机地址不能为空"
            p == null || p < 1 || p > 65535 -> saveError = "端口必须是 1-65535 的数字"
            verifyToken.isBlank() -> saveError = "VerifyToken 是监控数据接口的必填凭据"
            else -> {
                saveError = null
                val config = ServerConfig(
                    id = serverId ?: ServerConfig.newId(),
                    label = label.trim(),
                    host = h,
                    port = p,
                    verifyToken = verifyToken.filter { it.code in 32..126 }.trim(),
                    apiKey = apiKey.filter { it.code in 32..126 }.trim(),
                    controlTransport = transport,
                    voicePort = voicePortText.trim().toIntOrNull() ?: 0,
                    refetchIntervalMs = intervalSec.toLong() * 1000,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
                scope.launch {
                    try {
                        store.upsert(config)
                    } catch (e: Exception) {
                        saveError = "保存失败:凭据加密异常(${e.message ?: e.javaClass.simpleName}),配置未写入"
                        return@launch
                    }
                    ControlRepository.closeServer(config.id)
                    if (serverId == null) store.setActive(config.id)
                    onDone()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (serverId == null) "添加服务器" else "编辑服务器",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "对应游戏服务器上的 SLDataAPI 插件(端口默认 8081)。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("显示名称(可选)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("主机(IP 或域名)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("端口(默认 8081)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            OutlinedTextField(
                value = verifyToken,
                onValueChange = { verifyToken = it },
                label = { Text("VerifyToken(数据面凭据,必填)") },
                supportingText = { Text("插件 config.yml 中的 verify_token,用于拉取监控数据") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key(控制面凭据,可选)") },
                supportingText = { Text("sld_live_ / sld_duty_ 开头,权限在创建时就定了。要 admin 权限请在游戏里新建 admin Key 再换上;不填则只能看监控") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            OutlinedTextField(
                value = voicePortText,
                onValueChange = { voicePortText = it },
                label = { Text("语音流端口(可选)") },
                supportingText = { Text("留空 = 自动从监控读取;服务器 voice_port 非默认 8082 或监控不可用时必填") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            Column {
                Text("控制传输模式", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = transport == "http",
                        onClick = { transport = "http" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("HTTP") }
                    SegmentedButton(
                        selected = transport == "ws",
                        onClick = { transport = "ws" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("WS") }
                }
                Text(
                    "需与插件 control_transport 配置一致;WS 模式才能接收实时事件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column {
                Text(
                    "监控轮询间隔:${intervalSec.toInt()} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = intervalSec,
                    onValueChange = { intervalSec = it },
                    valueRange = 3f..30f,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { testConnection() }, enabled = !testing, modifier = Modifier.weight(1f)) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
                Button(onClick = { save() }, modifier = Modifier.weight(1f)) {
                    Text("保存")
                }
            }
            testResult?.let { (ok, message) ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                )
            }
            saveError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text(
                "凭据仅保存在本机(AndroidKeyStore 加密),不经过任何第三方服务器。" +
                    "SLDataAPI 使用明文 HTTP/WS,建议仅在可信网络或反向代理 TLS 后使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
