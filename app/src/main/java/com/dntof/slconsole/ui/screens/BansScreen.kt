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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dntof.slconsole.data.model.BanEntry
import com.dntof.slconsole.data.model.BanListData
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.components.PromptDialog
import com.dntof.slconsole.ui.components.PromptField
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.util.Format
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun BansScreen() {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var bans by remember { mutableStateOf<List<BanEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<BanEntry?>(null) }

    fun load() {
        val target = server ?: return
        scope.launch {
            val outcome = ControlRepository.call(target, "/control/moderation/ban_list")
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    bans = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(BanListData.serializer(), it) }.getOrNull()
                    }?.bans ?: emptyList()
                    error = null
                }
                is ControlRepository.ControlOutcome.Failure -> error = outcome.message
            }
        }
    }

    LaunchedEffect(server?.id) { load() }

    Column(Modifier.fillMaxSize()) {
        if (server?.hasControl != true) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "封禁管理需要控制面 API Key。请在服务器设置中配置后重试。",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SectionCard(
                "封禁列表",
                subtitle = bans?.let { "共 ${it.size} 条记录" } ?: "加载中…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                actions = {
                    IconButton(onClick = { load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "新增封禁") }
                },
            ) {
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        val list = bans
        if (list != null) {
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Gavel, "暂无封禁记录", "通过下方按钮可添加离线封禁")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.size) { index ->
                        val ban = list[index]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            ban.originalName?.takeIf { it.isNotBlank() } ?: ban.userId,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                        )
                                    }
                                    InfoChip(
                                        if (ban.banType == "ip") "IP 封禁" else "Steam 封禁",
                                        MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                KeyValueRow("UserID", ban.userId, mono = true)
                                KeyValueRow("原因", ban.reason?.takeIf { it.isNotBlank() } ?: "-")
                                KeyValueRow("签发", "${ban.issuer ?: "-"} · ${Format.banExpiry(ban.issuanceTime)}")
                                KeyValueRow("到期", Format.banExpiry(ban.expires))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { revokeTarget = ban }) {
                                        Text("解封", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        PromptDialog(
            title = "新增离线封禁",
            subtitle = "封禁类型:steam(按账号)或 ip(按地址)",
            confirmLabel = "封禁",
            danger = true,
            fields = listOf(
                PromptField("user_id", "UserID(如 76561198…@steam)"),
                PromptField("original_name", "玩家昵称(可选)"),
                PromptField("reason", "原因"),
                PromptField("duration", "时长(分钟,0=永久)", initial = "0", keyboardType = KeyboardType.Number),
                PromptField("ban_type", "类型(steam / ip)", initial = "steam"),
            ),
            onConfirm = { v ->
                val userId = v["user_id"].orEmpty().trim()
                if (userId.isNotEmpty()) {
                    scope.launch {
                        val outcome = ControlRepository.call(
                            server!!,
                            "/control/moderation/ban/add",
                            buildJsonObject {
                                put("user_id", userId)
                                put("original_name", v["original_name"].orEmpty().trim())
                                put("reason", v["reason"].orEmpty().trim().ifBlank { "违规行为" })
                                put("duration", v["duration"]?.toIntOrNull() ?: 0)
                                put("ban_type", v["ban_type"].orEmpty().trim().ifBlank { "steam" })
                            },
                        )
                        snackbar.showOutcome(outcome, "封禁已添加")
                        load()
                    }
                }
            },
            onDismiss = { showAdd = false },
        )
    }

    revokeTarget?.let { ban ->
        ConfirmDialog(
            title = "解封 ${ban.originalName ?: ban.userId}?",
            text = "类型:${ban.banType}",
            confirmLabel = "解封",
            danger = true,
            onConfirm = {
                scope.launch {
                    val outcome = ControlRepository.call(
                        server!!,
                        "/control/moderation/ban/revoke",
                        buildJsonObject {
                            put("user_id", ban.userId)
                            put("ban_type", ban.banType)
                            put("reason", "移动端解封")
                        },
                    )
                    snackbar.showOutcome(outcome, "已解封")
                    load()
                }
            },
            onDismiss = { revokeTarget = null },
        )
    }
}
