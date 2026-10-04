// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.PluginInfo
import com.dntof.slconsole.data.model.PluginListData
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dntof.slconsole.ui.withBottomChrome

/** 逐字段安全提取:插件响应里 priority/version 等字段在 LabAPI 与 EXILED 间类型不一致,避免整表解析失败。 */
private fun parsePlugin(o: JsonObject): PluginInfo? {
    fun text(key: String): String? =
        (o[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    fun bool(key: String): Boolean? =
        (o[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toBooleanStrictOrNull()
    val name = text("name") ?: return null
    return PluginInfo(
        name = name,
        author = text("author"),
        version = text("version"),
        prefix = text("prefix"),
        priority = text("priority"),
        enabled = bool("enabled") ?: false,
        self = bool("self"),
        staged = bool("staged"),
        source = text("source"),
    )
}

@Composable
fun PluginsScreen(onOpenAdapted: () -> Unit = {}) {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var plugins by remember { mutableStateOf<List<PluginInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    fun load() {
        val target = server ?: return
        scope.launch {
            val outcome = ControlRepository.call(target, "/control/plugins")
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    plugins = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(PluginListData.serializer(), it) }.getOrNull()
                    }?.plugins ?: emptyList()
                    error = null
                }
                is ControlRepository.ControlOutcome.Failure -> error = outcome.message
            }
        }
    }

    fun call(body: kotlinx.serialization.json.JsonObject, okText: String) {
        val target = server ?: return
        scope.launch {
            snackbar.showOutcome(ControlRepository.call(target, "/control/plugins", body), okText)
            load()
        }
    }

    LaunchedEffect(server?.id) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            TextButton(onClick = onOpenAdapted) {
                Text("适配插件:SLPlayer / OmegaWarhead")
            }
            SectionCard(
                "已加载插件",
                subtitle = plugins?.let { "共 ${it.size} 个(EXILED / LabAPI)" } ?: "加载中…",
                actions = {
                    TextButton(onClick = { pendingAction = "apply" }) { Text("应用暂存") }
                    TextButton(onClick = { pendingAction = "reload" }) { Text("重载") }
                },
            ) {
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "EXILED 插件应用暂存后立即生效;LabAPI 插件需重启回合后生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val list = plugins
        if (list == null) {
            item {
                Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (list.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.Extension,
                    "未发现插件",
                    "服务器上可能没有安装插件",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
                items(list.size) { index ->
                    val plugin = list[index]
                    AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Row) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        plugin.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "v${plugin.version ?: "?"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    InfoChip(
                                        when (plugin.source) {
                                            "exiled" -> "EXILED"
                                            "labapi" -> "LabAPI"
                                            else -> plugin.source ?: "未知"
                                        },
                                        MaterialTheme.colorScheme.secondary,
                                    )
                                    if (plugin.self == true) InfoChip("本插件", MaterialTheme.colorScheme.tertiary)
                                    if (plugin.staged == true) InfoChip("暂存变更", MaterialTheme.colorScheme.primary)
                                }
                                plugin.author?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "作者:$it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = plugin.enabled,
                                onCheckedChange = { enabled ->
                                    call(
                                        buildJsonObject {
                                            put("action", "stage")
                                            put("name", plugin.name)
                                            put("enabled", enabled)
                                        },
                                        if (enabled) "已暂存启用 ${plugin.name}" else "已暂存禁用 ${plugin.name}",
                                    )
                                },
                            )
                        }
                    }
                }
        }
    }

    pendingAction?.let { action ->
        ConfirmDialog(
            title = if (action == "apply") "应用暂存的插件变更?" else "重载全部插件?",
            text = if (action == "apply") "LabAPI 插件的启停将在重启回合后生效。" else "重载会重新加载服务器上的插件。",
            confirmLabel = if (action == "apply") "应用" else "重载",
            onConfirm = {
                call(
                    buildJsonObject { put("action", action) },
                    if (action == "apply") "已应用暂存" else "已触发重载",
                )
            },
            onDismiss = { pendingAction = null },
        )
    }
}
