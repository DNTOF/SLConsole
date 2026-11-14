// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import com.microsoft.clarity.modifiers.clarityMask
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

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

    LazyColumn(
        Modifier.fillMaxSize().clarityMask(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard(
                stringResource(R.string.common_reports),
                subtitle = reports?.let { pluralStringResource(R.plurals.reports_pending_auto_refresh_every_15, it.size.toInt(), it.size) } ?: stringResource(R.string.common_loading),
                actions = { TextButton(onClick = { load() }) { Text(stringResource(R.string.common_refresh)) } },
            ) {
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        val list = reports
        if (list == null) {
            item {
                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (list.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.CheckCircle,
                    stringResource(R.string.reports_all_reports_handled),
                    stringResource(R.string.reports_report_enabled_reports_players_submit),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
                items(list.size, key = { list[it].id }) { index ->
                    val report = list[index]
                    AppSurface(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(160)).fillMaxWidth(), role = GlassRole.Row) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        report.reporterName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.reports_anonymous_report),
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
                                InfoChip(stringResource(R.string.reports_pending), MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                stringResource(R.string.reports_reported, report.targetName?.takeIf { it.isNotBlank() } ?: "—"),
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
                                TextButton(onClick = { handleTarget = report }) { Text(stringResource(R.string.reports_mark_handled)) }
                            }
                        }
                    }
                }
        }
    }

    handleTarget?.let { report ->
        ConfirmDialog(
            title = stringResource(R.string.reports_handle_report),
            text = stringResource(R.string.reports_mark_report_as_handled, report.targetName ?: report.targetSteam64 ?: report.id),
            confirmLabel = stringResource(R.string.common_confirm),
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
                    snackbar.showOutcome(outcome, appString(R.string.reports_marked_as_handled))
                    load()
                }
            },
            onDismiss = { handleTarget = null },
        )
    }
}
