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
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

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
            testResult = false to (hostError ?: appString(R.string.common_enter_host_address_verifytoken_first))
            return
        }
        val pin = if (fingerprint.isBlank()) "" else CertFingerprint.normalize(fingerprint)
        if (fingerprint.isNotBlank() && pin == null) {
            testResult = false to appString(R.string.common_fingerprint_must_32_byte_sha)
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
                    val lock = if (result.encrypted) appString(R.string.common_encrypted) else appString(R.string.common_plaintext)
                    testResult = true to appString(R.string.server_edit_connected_online, lock, name ?: appString(R.string.server_edit_unnamed_server), count)
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
            saveError = appString(R.string.server_edit_still_reading_original_config_please)
            return
        }
        val h = host.trim()
        val p = port.toIntOrNull()
        val hostError = HostAddress.problem(h)
        val typedPin = if (fingerprint.isBlank()) "" else CertFingerprint.normalize(fingerprint)
        when {
            hostError != null -> saveError = hostError
            p == null || p < 1 || p > 65535 -> saveError = appString(R.string.server_edit_port_must_number_from_1)
            verifyToken.isBlank() -> saveError = appString(R.string.server_edit_verifytoken_required_credential_monitoring_data)
            fingerprint.isNotBlank() && typedPin == null -> saveError = appString(R.string.common_fingerprint_must_32_byte_sha)
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
                        saveError = appString(R.string.server_edit_save_failed_credential_encryption_error, e.message ?: e.javaClass.simpleName)
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
                if (serverId == null) stringResource(R.string.common_add_server) else stringResource(R.string.server_edit_edit_server),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.server_edit_corresponds_sldataapi_plugin_game_server),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.server_edit_display_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            val hostError = if (host.isBlank()) null else HostAddress.problem(host.trim())
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.server_edit_host_ip_domain)) },
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
                label = { Text(stringResource(R.string.server_edit_port_default_8081)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            SecretOutlinedField(
                value = verifyToken,
                onValueChange = { verifyToken = it },
                label = { Text(stringResource(R.string.server_edit_verifytoken_data_plane_credential_required)) },
                supportingText = { Text(stringResource(R.string.server_edit_verify_token_from_plugins_config)) },
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            SecretOutlinedField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.server_edit_api_key_control_plane_credential)) },
                supportingText = { Text(stringResource(R.string.server_edit_starts_sld_live_sld_duty)) },
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            OutlinedTextField(
                value = voicePortText,
                onValueChange = { voicePortText = it },
                label = { Text(stringResource(R.string.server_edit_voice_stream_port_optional)) },
                supportingText = { Text(stringResource(R.string.server_edit_empty_read_automatically_from_monitoring)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().keepAboveIme(),
            )
        }
        item {
            Column {
                Text(stringResource(R.string.server_edit_control_transport_mode), style = MaterialTheme.typography.bodyMedium)
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
                    stringResource(R.string.server_edit_must_match_plugins_control_transport),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.server_edit_certificate_fingerprint), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (tlsSeen) stringResource(R.string.server_edit_locked_server_has_connected_encryption)
                    else stringResource(R.string.server_edit_not_locked_yet_connections_are),
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
                    label = { Text(stringResource(R.string.server_edit_paste_fingerprint_advance_optional)) },
                    supportingText = { Text(stringResource(R.string.server_edit_sha_256_colons_spaces_are)) },
                    modifier = Modifier.fillMaxWidth().keepAboveIme(),
                    minLines = 2,
                )
                if (tlsSeen && serverId != null) {
                    OutlinedButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.server_edit_reset_encryption_record)) }
                }
            }
        }
        item {
            Column {
                Text(
                    stringResource(R.string.server_edit_monitoring_poll_interval, intervalSec.toInt()),
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
                    Text(if (testing) stringResource(R.string.common_testing) else stringResource(R.string.common_test_connection))
                }
                Button(onClick = { save() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_save))
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
                stringResource(R.string.server_edit_credentials_are_stored_only_phone),
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
            title = stringResource(R.string.server_edit_reset_encryption_record_2),
            text = stringResource(R.string.server_edit_after_resetting_app_will_allow),
            confirmLabel = stringResource(R.string.server_edit_reset_anyway),
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
