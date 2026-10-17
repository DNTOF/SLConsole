// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.remote.tls.CertFingerprint
import com.dntof.slconsole.data.remote.tls.ServerSecurity
import com.dntof.slconsole.data.remote.tls.TlsMessages
import com.dntof.slconsole.data.remote.tls.TransportStatus
import com.dntof.slconsole.ui.rememberActiveServer
import kotlinx.coroutines.launch

@Composable
fun FingerprintText(fingerprint: String, modifier: Modifier = Modifier) {
    val canonical = CertFingerprint.normalize(fingerprint)
    Text(
        canonical?.let { CertFingerprint.groupedLines(it) } ?: fingerprint,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
fun TofuDialog(fingerprint: String, onTrust: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认服务器证书") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("第一次用加密连接这台服务器。请把下面的指纹和服务器控制台对照，一致后再信任。")
                Text(
                    "看 sldataapi cert show，或启动时印出的 TLS 横幅里的 SHA-256。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FingerprintText(fingerprint)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text("信任此指纹") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("暂不信任") } },
    )
}

@Composable
fun MismatchDialog(
    pinned: String,
    presented: String,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("证书指纹变了") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("已固定的指纹和服务器现在出示的不一样。可能是管理员执行了 sldataapi cert regen，也可能有人在中间拦截。请对照控制台后再决定。应用不会改走明文。")
                Text("已固定", style = MaterialTheme.typography.labelMedium)
                FingerprintText(pinned)
                Text("现在出示", style = MaterialTheme.typography.labelMedium)
                FingerprintText(presented)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text("我已核对，改信新指纹") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("保持断开") } },
    )
}

@Composable
fun TlsStatusBanner() {
    val server = rememberActiveServer() ?: return
    val servers by TransportStatus.servers.collectAsState()
    val ui = servers[server.id] ?: return
    when {
        ui.plaintext -> BannerRow(
            TlsMessages.PLAINTEXT_BANNER,
            TlsMessages.PLAINTEXT_DETAIL,
            onOpen = null,
        )
        ui.prompt == null && ui.hold is TransportStatus.Prompt.Tofu -> BannerRow(
            "需要确认证书指纹",
            "对照服务器控制台后再信任。",
            onOpen = { TransportStatus.reopen(server.id) },
        )
        ui.prompt == null && ui.hold is TransportStatus.Prompt.Mismatch -> BannerRow(
            "证书指纹不一致",
            "加密已停下。点开后可对照控制台，再决定是否改信新指纹。",
            onOpen = { TransportStatus.reopen(server.id) },
        )
    }
}

@Composable
fun TlsPromptHost(serverId: String?) {
    if (serverId == null) return
    val servers by TransportStatus.servers.collectAsState()
    val prompt = servers[serverId]?.prompt ?: return
    val scope = rememberCoroutineScope()
    when (prompt) {
        is TransportStatus.Prompt.Tofu -> TofuDialog(
            fingerprint = prompt.fingerprint,
            onTrust = { scope.launch { ServerSecurity.trust(serverId, prompt.fingerprint) } },
            onDismiss = { TransportStatus.dismiss(serverId) },
        )
        is TransportStatus.Prompt.Mismatch -> MismatchDialog(
            pinned = prompt.pinned,
            presented = prompt.presented,
            onTrust = { scope.launch { ServerSecurity.trust(serverId, prompt.presented) } },
            onDismiss = { TransportStatus.dismiss(serverId) },
        )
    }
}

@Composable
private fun BannerRow(title: String, detail: String, onOpen: (() -> Unit)?) {
    val color = MaterialTheme.colorScheme.onErrorContainer
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = color)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = color)
        }
        if (onOpen != null) {
            TextButton(onClick = onOpen) { Text("查看") }
        }
    }
}
