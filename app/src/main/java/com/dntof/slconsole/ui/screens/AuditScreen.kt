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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.AuditEntry
import com.dntof.slconsole.data.model.AuditListData
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.util.Format
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun AuditScreen() {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<AuditEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        val target = server ?: return
        scope.launch {
            val outcome = ControlRepository.call(
                target,
                "/control/audit/list",
                buildJsonObject { put("limit", 200) },
            )
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    entries = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(AuditListData.serializer(), it) }.getOrNull()
                    }?.entries ?: emptyList()
                    error = null
                }
                is ControlRepository.ControlOutcome.Failure -> error = outcome.message
            }
        }
    }

    LaunchedEffect(server?.id) { load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("控制审计", style = MaterialTheme.typography.titleMedium)
                Text(
                    entries?.let { "最近 ${it.size} 条操作记录(新→旧)" } ?: "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { load() }) { Text("刷新") }
        }
        error?.let {
            Text(
                it,
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val list = entries
        if (list == null) return
        if (list.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Outlined.FactCheck, "暂无审计记录", "控制操作执行后会记录在这里")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list.size) { index ->
                    val entry = list[index]
                    var expanded by remember(entry.time) { mutableStateOf(false) }
                    AppSurface(
                        Modifier.fillMaxWidth(),
                        onClick = { expanded = !expanded },
                        role = GlassRole.Row,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (entry.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = if (entry.success) androidx.compose.ui.graphics.Color(0xFF4ADE80)
                                    else MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    entry.endpoint ?: "-",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    entry.time?.take(19)?.replace("T", " ") ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            KeyValueRow("操作者", entry.actor ?: "-", mono = true)
                            entry.message?.takeIf { it.isNotBlank() }?.let {
                                KeyValueRow("结果", it, mono = true)
                            }
                            if (expanded) {
                                entry.body?.takeIf { it.isNotBlank() }?.let {
                                    KeyValueRow("请求体", it, mono = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
