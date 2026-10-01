package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Flag
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
import com.dntof.slconsole.data.model.ReportRecord
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.rememberActiveServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dntof.slconsole.ui.withBottomChrome

private fun formatReportTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            .format(Date(java.time.Instant.parse(iso).toEpochMilli()))
    }.getOrDefault(iso.take(19).replace("T", " "))
}

@Composable
fun ReportsScreen() {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var reports by remember { mutableStateOf<List<ReportRecord>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var handleTarget by remember { mutableStateOf<ReportRecord?>(null) }

    fun load() {
        val target = server ?: return
        scope.launch {
            val outcome = ControlRepository.call(
                target,
                "/control/reports",
                buildJsonObject { put("action", "list") },
            )
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    reports = outcome.data?.let {
                        runCatching {
                            AppJson.json.decodeFromJsonElement(
                                ListSerializer(ReportRecord.serializer()), it,
                            )
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    error = null
                }
                is ControlRepository.ControlOutcome.Failure -> error = outcome.message
            }
        }
    }

    LaunchedEffect(server?.id) {
        while (true) {
            load()
            delay(15_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SectionCard(
            "举报管理",
            subtitle = reports?.let { "${it.size} 条待处理 · 每 15 秒自动刷新" } ?: "加载中…",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            actions = { TextButton(onClick = { load() }) { Text("刷新") } },
        ) {
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        val list = reports
        if (list == null) return
        if (list.isEmpty()) {
            EmptyState(
                Icons.Filled.CheckCircle,
                "所有举报均已处理",
                "启用 report_enabled 后,玩家通过服务器设置面板提交的举报会显示在这里。",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp).withBottomChrome(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list.size, key = { list[it].id }) { index ->
                    val report = list[index]
                    AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Row) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        report.reporterName?.takeIf { it.isNotBlank() } ?: "匿名举报",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${report.reporterSteam64 ?: "—"} · ${report.reporterIp ?: "—"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                    )
                                }
                                InfoChip("待处理", MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "被举报:${report.targetName?.takeIf { it.isNotBlank() } ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                report.targetSteam64 ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                            if (!report.reason.isNullOrBlank()) {
                                Text(
                                    report.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    formatReportTime(report.reportedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { handleTarget = report }) { Text("标记已处理") }
                            }
                        }
                    }
                }
            }
        }
    }

    handleTarget?.let { report ->
        ConfirmDialog(
            title = "处理举报",
            text = "确认将此举报标记为已处理:${report.targetName ?: report.targetSteam64 ?: report.id}",
            confirmLabel = "确认",
            onConfirm = {
                val target = server ?: return@ConfirmDialog
                scope.launch {
                    val outcome = ControlRepository.call(
                        target,
                        "/control/reports",
                        buildJsonObject {
                            put("action", "handle")
                            put("id", report.id)
                        },
                    )
                    snackbar.showOutcome(outcome, "已标记为处理完成")
                    load()
                }
            },
            onDismiss = { handleTarget = null },
        )
    }
}
