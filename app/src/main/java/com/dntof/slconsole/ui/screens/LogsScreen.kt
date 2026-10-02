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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import com.dntof.slconsole.ui.bottomChromePadding
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.LogFile
import com.dntof.slconsole.data.model.LogTailData
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.components.LabeledSwitch
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dntof.slconsole.ui.withBottomChrome

private val LINE_OPTIONS = listOf(100, 200, 500, 1000, 2000)

@Composable
fun LogsScreen() {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<LogFile>?>(null) }
    var selected by remember { mutableStateOf<LogFile?>(null) }
    var tail by remember { mutableStateOf<LogTailData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lines by rememberSaveable { mutableIntStateOf(200) }
    var filter by rememberSaveable { mutableStateOf("") }
    var auto by rememberSaveable { mutableStateOf(false) }

    fun loadFiles() {
        val target = server ?: return
        scope.launch {
            val outcome = ControlRepository.call(
                target,
                "/control/logs",
                buildJsonObject { put("action", "list") },
            )
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    files = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(com.dntof.slconsole.data.model.LogListData.serializer(), it) }.getOrNull()
                    }?.files ?: emptyList()
                    error = null
                }
                is ControlRepository.ControlOutcome.Failure -> error = outcome.message
            }
        }
    }

    fun loadTail() {
        val target = server ?: return
        val path = selected?.path ?: return
        scope.launch {
            val outcome = ControlRepository.call(
                target,
                "/control/logs",
                buildJsonObject {
                    put("path", path)
                    put("lines", lines)
                    put("filter", filter)
                },
            )
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    tail = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(LogTailData.serializer(), it) }.getOrNull()
                    }
                    error = null
                }
                is ControlRepository.ControlOutcome.Failure -> error = outcome.message
            }
        }
    }

    LaunchedEffect(server?.id, selected == null) {
        if (selected == null) loadFiles()
    }
    LaunchedEffect(server?.id, selected?.path, lines) {
        if (selected != null) loadTail()
    }
    LaunchedEffect(selected?.path, auto, lines) {
        while (selected != null && auto) {
            delay(5000)
            loadTail()
        }
    }

    if (selected == null) {
        Column(Modifier.fillMaxSize()) {
            SectionCard(
                "日志文件",
                subtitle = files?.let { "共 ${it.size} 个文件" } ?: "加载中…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            val list = files
            if (list == null || list.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Outlined.Article, "没有日志文件", "检查插件 log_directory 配置")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp).withBottomChrome(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.path }) { file ->
                        AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Row) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(file.name ?: file.path, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        file.modified ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    Format.bytes(file.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().bottomChromePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected = null; tail = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回列表")
                }
                Column(Modifier.weight(1f)) {
                    Text(selected?.name ?: selected?.path ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    tail?.let {
                        Text(
                            "共 ${it.total} 行 · 显示最近 ${it.lines.size} 行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LINE_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = lines == option,
                        onClick = { lines = option },
                        label = { Text("$option") },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("关键字过滤") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = { loadTail() }) { Text("刷新") }
            }
            LabeledSwitch(
                "自动刷新(每 5 秒)",
                null,
                auto,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { auto = it }
            val tailData = tail
            if (tailData != null) {
                AppSurface(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    role = GlassRole.Panel,
                ) {
                    Text(
                        tailData.lines.joinToString("\n"),
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                Text(
                    error ?: "加载中…",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = error?.let { MaterialTheme.colorScheme.error } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
