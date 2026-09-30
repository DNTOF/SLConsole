package com.dntof.slconsole.ui.screens

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
import com.dntof.slconsole.data.model.FileReadData
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.util.Format
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
                is ControlRepository.ControlOutcome.Failure -> listError = outcome.message
            }
        }
    }

    fun openFile(entry: FileEntry) {
        val target = server ?: return
        val filePath = joinPath(path, entry.name)
        editing = EditingFile(path = filePath, content = "", size = entry.size)
        readError = null
        scope.launch {
            val outcome = ControlRepository.call(
                target,
                "/control/files/read",
                buildJsonObject { put("path", filePath) },
            )
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    val data = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(FileReadData.serializer(), it) }.getOrNull()
                    }
                    editing = if (data != null) {
                        EditingFile(path = filePath, content = data.content, size = data.size)
                    } else {
                        null
                    }
                    if (data == null) readError = "文件内容解析失败"
                }
                is ControlRepository.ControlOutcome.Failure -> readError = outcome.message
            }
        }
    }

    fun doSave(file: EditingFile) {
        val target = server ?: return
        saving = true
        scope.launch {
            val outcome = ControlRepository.call(
                target,
                "/control/files/write",
                buildJsonObject {
                    put("path", file.path)
                    put("content", file.content)
                },
            )
            snackbar.showOutcome(outcome, "已保存")
            saving = false
            editing = null
            load(path)
        }
    }

    LaunchedEffect(server?.id, path) { load(path) }

    Column(Modifier.fillMaxSize()) {
        // 路径导航 + 操作
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { path = parentPath(path) },
                enabled = path.isNotEmpty(),
            ) { Text("上级") }
            TextButton(onClick = { path = "" }) { Text("根目录") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { load(path) }) { Text("刷新") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathChip("根", path.isEmpty()) { path = "" }
            var acc = ""
            path.split("/").forEach { segment ->
                if (segment.isBlank()) return@forEach
                acc = if (acc.isEmpty()) segment else "$acc/$segment"
                Text(" / ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PathChip(segment, acc == path) { path = acc }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        val file = editing
        if (file != null) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { editing = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回列表")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.path,
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
                OutlinedTextField(
                    value = file.content,
                    onValueChange = { editing = file.copy(content = it) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = { editing = null }, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = { saveConfirm = true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Save, null, Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (saving) "保存中…" else "保存")
                    }
                }
            }
        } else {
            SectionCard(
                "文件浏览",
                subtitle = listing?.let { "${it.count} 项 · ${if (it.path.isEmpty()) "FileRoot 根目录" else it.path}" } ?: "加载中…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                listError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            val entries = listing?.entries
            if (entries != null) {
                if (entries.isEmpty()) {
                    EmptyState(Icons.Outlined.Folder, "目录为空", "在 SLDataAPI 配置中设置的 FileRoot 为空")
                } else {
                    val sorted = entries.sortedWith(
                        compareByDescending<FileEntry> { it.type == "dir" }.thenBy { it.name },
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(sorted.size, key = { sorted[it].name + sorted[it].type }) { index ->
                            val entry = sorted[index]
                            AppSurface(
                                Modifier.fillMaxWidth(),
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
                                    if (entry.isProtected) InfoChip("受保护", MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val confirmFile = editing
    if (saveConfirm && confirmFile != null) {
        ConfirmDialog(
            title = "确认保存",
            text = "确认保存对 ${confirmFile.path} 的修改?",
            confirmLabel = "保存",
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
