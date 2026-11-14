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
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R

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
        title = { Text(stringResource(R.string.tls_prompts_confirm_server_certificate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tls_prompts_first_encrypted_connection_server_compare))
                Text(
                    stringResource(R.string.tls_prompts_see_sldataapi_cert_show_sha),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FingerprintText(fingerprint)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text(stringResource(R.string.tls_prompts_trust_fingerprint)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tls_prompts_not_now)) } },
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
        title = { Text(stringResource(R.string.tls_prompts_certificate_fingerprint_changed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tls_prompts_pinned_fingerprint_differs_from_one))
                Text(stringResource(R.string.tls_prompts_pinned), style = MaterialTheme.typography.labelMedium)
                FingerprintText(pinned)
                Text(stringResource(R.string.tls_prompts_presented_now), style = MaterialTheme.typography.labelMedium)
                FingerprintText(presented)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text(stringResource(R.string.tls_prompts_ive_checked_trust_new_fingerprint)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tls_prompts_stay_disconnected)) } },
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
            stringResource(R.string.tls_prompts_certificate_fingerprint_needs_confirmation),
            stringResource(R.string.tls_prompts_compare_server_console_before_trusting),
            onOpen = { TransportStatus.reopen(server.id) },
        )
        ui.prompt == null && ui.hold is TransportStatus.Prompt.Mismatch -> BannerRow(
            stringResource(R.string.common_certificate_fingerprint_mismatch),
            stringResource(R.string.tls_prompts_encryption_has_stopped_tap_compare),
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
            TextButton(onClick = onOpen) { Text(stringResource(R.string.tls_prompts_view)) }
        }
    }
}
