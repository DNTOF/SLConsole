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
import com.dntof.slconsole.data.model.HostAddress
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.SlHttpClient
import com.dntof.slconsole.data.remote.tls.CertFingerprint
import com.dntof.slconsole.data.remote.tls.TransportFailure
import com.dntof.slconsole.data.remote.tls.TransportStatus
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.FingerprintText
import com.dntof.slconsole.ui.components.MismatchDialog
import com.dntof.slconsole.ui.components.SecretOutlinedField
import com.dntof.slconsole.ui.components.TofuDialog
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
    // 令牌不进 saved state，避免旋转或进程重建时写进系统保存的界面状态。
    var verifyToken by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var transport by rememberSaveable { mutableStateOf("http") }
    var voicePortText by rememberSaveable { mutableStateOf("") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var tlsSeen by rememberSaveable { mutableStateOf(false) }
    var intervalSec by remember { mutableFloatStateOf((ServerConfig.DEFAULT_INTERVAL_MS / 1000).toFloat()) }
    var prefilled by remember { mutableStateOf(serverId == null) }
    var fingerprintDirty by remember { mutableStateOf(false) }
    var tlsSeenDirty by remember { mutableStateOf(false) }
    var trustPrompt by remember { mutableStateOf<TransportStatus.Prompt?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

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
        fingerprint = cfg.certFingerprint
        tlsSeen = cfg.tlsSeen
        intervalSec = (cfg.refetchIntervalMs / 1000).toFloat()
    }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun testConnection() {
        val h = host.trim()
        val p = port.toIntOrNull() ?: ServerConfig.DEFAULT_PORT
        val hostError = HostAddress.problem(h)
        if (hostError != null || verifyToken.isBlank()) {
            testResult = false to (hostError ?: "请先填写主机地址和 VerifyToken")
            return
        }
        val pin = if (fingerprint.isBlank()) "" else CertFingerprint.normalize(fingerprint)
        if (fingerprint.isNotBlank() && pin == null) {
            testResult = false to "指纹应为 32 字节的 SHA-256，可以带冒号或空格"
            return
        }
        testing = true
        testResult = null
        scope.launch {
            val temp = ServerConfig(
                id = "test",
                host = h,
                port = p,
                verifyToken = verifyToken,
                certFingerprint = pin.orEmpty(),
                tlsSeen = tlsSeen,
            )
            when (val result = SlHttpClient().getData(temp, "/get_sl_data", useCache = false)) {
                is SlHttpClient.HttpResult.Success -> {
                    val name = (result.body["server_name"] as? JsonPrimitive)?.content?.let { stripRichText(it) }
                    val count = (result.body["players_count"] as? JsonPrimitive)?.content
                    val lock = if (result.encrypted) "加密" else "明文"
                    testResult = true to "连接成功（$lock）:${name ?: "(未命名服务器)"}(在线 $count 人)"
                }
                is SlHttpClient.HttpResult.Failure -> {
                    testResult = false to result.message
                    trustPrompt = when (val tls = result.tls) {
                        is TransportFailure.Tofu -> TransportStatus.Prompt.Tofu(tls.fingerprint)
                        is TransportFailure.Mismatch -> TransportStatus.Prompt.Mismatch(tls.pinned, tls.presented)
                        else -> null
                    }
                }
            }
            testing = false
        }
    }

    fun save() {
        if (serverId != null && !prefilled) {
            saveError = "正在读取原配置，请稍候再保存"
            return
        }
        val h = host.trim()
        val p = port.toIntOrNull()
        val hostError = HostAddress.problem(h)
        val typedPin = if (fingerprint.isBlank()) "" else CertFingerprint.normalize(fingerprint)
        when {
            hostError != null -> saveError = hostError
            p == null || p < 1 || p > 65535 -> saveError = "端口必须是 1-65535 的数字"
            verifyToken.isBlank() -> saveError = "VerifyToken 是监控数据接口的必填凭据"
            fingerprint.isNotBlank() && typedPin == null -> saveError = "指纹应为 32 字节的 SHA-256，可以带冒号或空格"
            else -> {
                saveError = null
                scope.launch {
                    val stored = serverId?.let { id -> store.serversFlow.first().find { it.id == id } }
                    val pin = if (fingerprintDirty || stored == null) typedPin.orEmpty() else stored.certFingerprint
                    val seen = if (tlsSeenDirty || stored == null) tlsSeen else stored.tlsSeen
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
                        createdAt = existing?.createdAt ?: stored?.createdAt ?: System.currentTimeMillis(),
                        certFingerprint = pin,
                        tlsSeen = seen,
                    )
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
            val hostError = if (host.isBlank()) null else HostAddress.problem(host.trim())
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("主机(IP 或域名)") },
                isError = hostError != null,
                supportingText = hostError?.let { message -> { Text(message) } },
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
            SecretOutlinedField(
                value = verifyToken,
                onValueChange = { verifyToken = it },
                label = { Text("VerifyToken(数据面凭据,必填)") },
                supportingText = { Text("插件 config.yml 中的 verify_token,用于拉取监控数据") },
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            SecretOutlinedField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key(控制面凭据,可选)") },
                supportingText = { Text("sld_live_ / sld_duty_ 开头,权限在创建时就定了。要 admin 权限请在游戏里新建 admin Key 再换上;不填则只能看监控") },
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("证书指纹", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (tlsSeen) "已锁定：这台服务器用加密连通过，不会自动改回明文。"
                    else "还没锁定。插件支持 TLS 时会加密；旧版或关闭 TLS 时走明文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val shown = CertFingerprint.normalize(fingerprint)
                if (shown != null) FingerprintText(shown)
                OutlinedTextField(
                    value = fingerprint,
                    onValueChange = {
                        fingerprint = it
                        fingerprintDirty = true
                    },
                    label = { Text("预先粘贴指纹（可选）") },
                    supportingText = { Text("SHA-256，可以带冒号或空格。留空则第一次加密连接时再确认。") },
                    modifier = Modifier.fillMaxWidth().keepAboveIme(),
                    minLines = 2,
                )
                if (tlsSeen && serverId != null) {
                    OutlinedButton(onClick = { confirmReset = true }) { Text("重置加密记录") }
                }
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
                "凭据只保存在这台手机上（AndroidKeyStore 加密），只发给你自己的服务器，不经过任何第三方。" +
                    "插件支持 TLS 时走 HTTPS/WSS，并固定服务器自签证书。第一次连接请对照控制台指纹（sldataapi cert show，或启动时的 TLS 横幅）再信任。" +
                    "需要带 TLS 的 SLDataAPI；旧版或 tls_mode 为 off 时会显示「此连接不加密」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    when (val prompt = trustPrompt) {
        is TransportStatus.Prompt.Tofu -> TofuDialog(
            fingerprint = prompt.fingerprint,
            onTrust = {
                CertFingerprint.normalize(prompt.fingerprint)?.let {
                    fingerprint = it
                    fingerprintDirty = true
                }
                trustPrompt = null
            },
            onDismiss = { trustPrompt = null },
        )
        is TransportStatus.Prompt.Mismatch -> MismatchDialog(
            pinned = prompt.pinned,
            presented = prompt.presented,
            onTrust = {
                CertFingerprint.normalize(prompt.presented)?.let {
                    fingerprint = it
                    fingerprintDirty = true
                }
                trustPrompt = null
            },
            onDismiss = { trustPrompt = null },
        )
        null -> Unit
    }
    if (confirmReset && serverId != null) {
        ConfirmDialog(
            title = "重置加密记录？",
            text = "重置后，如果加密握手失败，应用会再次允许明文。只有确认这台服务器已经关掉 TLS 时才这么做。明文连接会一直显示「此连接不加密」。",
            confirmLabel = "仍然重置",
            danger = true,
            onConfirm = {
                tlsSeen = false
                tlsSeenDirty = true
                val id = serverId
                scope.launch { com.dntof.slconsole.data.remote.tls.ServerSecurity.resetLatch(id) }
            },
            onDismiss = { confirmReset = false },
        )
    }
}
