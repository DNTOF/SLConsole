// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R

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
        title = { Text(stringResource(R.string.update_dialog_new_version_available, info.versionName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildString {
                        append(stringResource(R.string.update_dialog_current, BuildConfig.VERSION_NAME, info.versionName))
                        info.sizeBytes?.let { append(stringResource(R.string.update_dialog_package, formatBytes(it))) }
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
                        notes.ifBlank { stringResource(R.string.update_dialog_no_release_notes_version) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.update_dialog_app_doesnt_verify_package_hash),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info.sha256?.let { sha ->
                    Text(stringResource(R.string.update_dialog_package_sha_256), style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(
                            sha,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } ?: Text(
                    stringResource(R.string.update_dialog_no_sha_256_digest_was),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) { Text(stringResource(R.string.update_dialog_update_now)) }
        },
        // 不包 Row:AlertDialog 的按钮区是 FlowRow,英文较长时三个按钮能自动换行。
        dismissButton = {
            TextButton(onClick = onIgnore) { Text(stringResource(R.string.update_dialog_ignore_version)) }
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_dialog_later)) }
        },
    )
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
