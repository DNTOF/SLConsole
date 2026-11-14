// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.FileEntry
import com.dntof.slconsole.data.model.FileListData
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.remote.BetaHints
import com.dntof.slconsole.data.remote.FileChunks
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.FileTransfer
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.microsoft.clarity.modifiers.clarityMask
import com.dntof.slconsole.ui.belowTopBar
import com.dntof.slconsole.ui.bottomChromePadding
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.ui.rememberServerBeta
import com.dntof.slconsole.util.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dntof.slconsole.ui.withBottomChrome
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

private data class EditingFile(
    val path: String,
    val content: String,
    val size: Long,
)

private fun joinPath(base: String, name: String): String =
    if (base.isNotEmpty()) "$base/$name" else name

private fun parentPath(p: String): String {
    val idx = p.lastIndexOf('/')
    return if (idx <= 0) "" else p.slice(0 until idx)
}

@Composable
fun FilesScreen() {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var path by rememberSaveable { mutableStateOf("") }
    var listing by remember { mutableStateOf<FileListData?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EditingFile?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var saveConfirm by remember { mutableStateOf<Boolean>(false) }
    var saving by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var transferJob by remember { mutableStateOf<Job?>(null) }
    val beta = rememberServerBeta()

    fun load(targetPath: String) {
        val target = server
        android.util.Log.d("FilesScreen", "load path=$targetPath server=" + (target?.id ?: "NULL") + " hasControl=" + (target?.hasControl ?: false))
        val target2 = target ?: return
        scope.launch {
            val outcome = ControlRepository.call(
                target2,
                "/control/files/list",
                buildJsonObject { put("path", targetPath) },
            )
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    listing = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(FileListData.serializer(), it) }.getOrNull()
                    }
                    listError = null
                }
                is ControlRepository.ControlOutcome.Failure -> listError = BetaHints.fileFailure(outcome)
            }
        }
    }

    fun openFile(entry: FileEntry) {
        val target = server ?: return
        val filePath = joinPath(path, entry.name)
        editing = EditingFile(path = filePath, content = "", size = entry.size)
        readError = null
        progressText = null
        progress = null
        val chunked = beta.fileChunks && entry.size > FileChunks.singleShotLimit(target.controlTransport == "ws")
        transferJob?.cancel()
        transferJob = scope.launch {
            val result = FileTransfer.readText(target, filePath, entry.size, chunked) { done, total ->
                progressText = appString(R.string.files_reading, Format.bytes(done), Format.bytes(total))
                progress = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
            }
            progressText = null
            progress = null
            when (result) {
                is FileTransfer.ReadResult.Ok -> editing = EditingFile(filePath, result.text, result.size)
                is FileTransfer.ReadResult.Failed -> readError = BetaHints.fileFailure(result.failure)
            }
        }
    }

    fun doSave(file: EditingFile) {
        val target = server ?: return
        saving = true
        progressText = null
        progress = null
        val chunked = beta.fileChunks &&
            file.content.toByteArray(Charsets.UTF_8).size > FileChunks.singleShotLimit(target.controlTransport == "ws")
        transferJob?.cancel()
        transferJob = scope.launch {
            val result = FileTransfer.writeText(target, file.path, file.content, chunked) { done, total ->
                progressText = appString(R.string.files_saving, Format.bytes(done), Format.bytes(total))
                progress = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
            }
            saving = false
            progressText = null
            progress = null
            when (result) {
                FileTransfer.WriteResult.Ok -> {
                    snackbar.showSnackbar(appString(R.string.files_saved))
                    editing = null
                    load(path)
                }
                is FileTransfer.WriteResult.Failed -> snackbar.showSnackbar(BetaHints.fileFailure(result.failure))
            }
        }
    }

    LaunchedEffect(server?.id, path) { load(path) }

    val file = editing
    if (file != null) {
        Column(
            Modifier
                .fillMaxSize()
                .clarityMask()
                .belowTopBar()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .bottomChromePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { editing = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back_list))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        file.path,
                        modifier = Modifier.clarityMask(),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                    )
                    Text(
                        Format.bytes(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            readError?.let {
                Text(
                    it,
                    Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            progressText?.let { label ->
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                progress?.let { fraction ->
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
            }
            if (beta.fileChunks) {
                Text(
                    stringResource(R.string.files_server_has_chunked_files_enabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = file.content,
                onValueChange = { editing = file.copy(content = it) },
                modifier = Modifier.fillMaxWidth().weight(1f).keepAboveIme(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        transferJob?.cancel()
                        saving = false
                        progressText = null
                        editing = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = { saveConfirm = true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Save, null, Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saving) stringResource(R.string.files_saving_2) else stringResource(R.string.common_save))
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().clarityMask(),
            contentPadding = PaddingValues(horizontal = 16.dp).withBottomChrome(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { path = parentPath(path) },
                        enabled = path.isNotEmpty(),
                    ) { Text(stringResource(R.string.files_up)) }
                    TextButton(onClick = { path = "" }) { Text(stringResource(R.string.files_root)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { load(path) }) { Text(stringResource(R.string.common_refresh)) }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PathChip(stringResource(R.string.files_root_2), path.isEmpty()) { path = "" }
                    var acc = ""
                    path.split("/").forEach { segment ->
                        if (segment.isBlank()) return@forEach
                        acc = if (acc.isEmpty()) segment else "$acc/$segment"
                        Text(" / ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PathChip(segment, acc == path) { path = acc }
                    }
                }
            }
            item {
                SectionCard(
                    stringResource(R.string.files_file_browser),
                    subtitle = listing?.let { pluralStringResource(R.plurals.files_items, it.count.toInt(), it.count) } ?: stringResource(R.string.common_loading),
                ) {
                    listError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            val entries = listing?.entries
            if (entries == null) {
                item { Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (entries.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.Folder,
                        stringResource(R.string.files_folder_empty),
                        stringResource(R.string.files_fileroot_set_sldataapi_config_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    )
                }
            } else {
                val sorted = entries.sortedWith(
                    compareByDescending<FileEntry> { it.type == "dir" }.thenBy { it.name },
                )
                items(sorted.size, key = { sorted[it].name + sorted[it].type }) { index ->
                    val entry = sorted[index]
                    AppSurface(
                        Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(160)).fillMaxWidth(),
                        enabled = !entry.isProtected,
                        onClick = {
                            if (entry.type == "dir") path = joinPath(path, entry.name)
                            else openFile(entry)
                        },
                        role = GlassRole.Row,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(
                                        if (entry.isProtected) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else if (entry.type == "dir") {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (entry.type == "dir") Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                                    null,
                                    Modifier.height(18.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (entry.type == "dir") "${entry.name}/" else entry.name,
                                    modifier = Modifier.clarityMask(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    color = if (entry.isProtected) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (entry.type == "file") {
                                    Text(
                                        "${Format.bytes(entry.size)} · ${entry.modified ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (entry.isProtected) InfoChip(stringResource(R.string.files_protected), MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    val confirmFile = editing
    if (saveConfirm && confirmFile != null) {
        ConfirmDialog(
            title = stringResource(R.string.files_confirm_save),
            text = stringResource(R.string.files_save_changes, confirmFile.path),
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { doSave(confirmFile) },
            onDismiss = { saveConfirm = false },
        )
    }
}

@Composable
private fun PathChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clarityMask()
            .background(
                if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
