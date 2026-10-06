// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.data.update.ReleaseNotes
import com.dntof.slconsole.data.update.UpdateInfo
import java.util.Locale

/** 发现新版本时的弹窗：版本、大小、更新说明，以及「立即更新」「稍后」「忽略此版本」。 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
) {
    val notes = remember(info.notes) { ReleaseNotes.toPlain(info.notes) }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildString {
                        append("当前 ${BuildConfig.VERSION_NAME} → ${info.versionName}")
                        info.sizeBytes?.let { append("　安装包 ${formatBytes(it)}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Column(
                    Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        notes.ifBlank { "这个版本没有写更新说明。" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider()
                Text(
                    "本应用不会自行校验安装包哈希。如果下面有 SHA-256，请下载后自己对比。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info.sha256?.let { sha ->
                    Text("安装包 SHA-256", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(
                            sha,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } ?: Text(
                    "这次没有提供 SHA-256 摘要。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) { Text("立即更新") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onIgnore) { Text("忽略此版本") }
                TextButton(onClick = onLater) { Text("稍后") }
            }
        },
    )
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
