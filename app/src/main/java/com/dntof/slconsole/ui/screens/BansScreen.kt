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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Refresh
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
import com.microsoft.clarity.modifiers.clarityMask
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
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.rememberActiveServer
import com.dntof.slconsole.util.Format
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.ui.text.input.KeyboardType
import com.dntof.slconsole.ui.withBottomChrome
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

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

    LazyColumn(
        Modifier.fillMaxSize().clarityMask(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            if (server?.hasControl != true) {
                AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Panel) {
                    Text(
                        stringResource(R.string.bans_ban_management_needs_control_plane),
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SectionCard(
                    stringResource(R.string.bans_ban_list),
                    subtitle = bans?.let { pluralStringResource(R.plurals.bans_records_total, it.size.toInt(), it.size) } ?: stringResource(R.string.common_loading),
                    actions = {
                        IconButton(onClick = { load() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.common_refresh)) }
                        IconButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.bans_add_ban)) }
                    },
                ) {
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        val list = bans
        if (list == null) {
            item {
                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (list.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.Gavel,
                    stringResource(R.string.bans_no_bans_yet),
                    stringResource(R.string.bans_use_button_below_add_offline),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
                    items(list.size) { index ->
                        val ban = list[index]
                        AppSurface(Modifier.fillMaxWidth(), role = GlassRole.Row) {
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
                                        if (ban.banType == "ip") stringResource(R.string.bans_ip_ban) else stringResource(R.string.bans_steam_ban),
                                        MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                KeyValueRow("UserID", ban.userId, mono = true)
                                KeyValueRow(stringResource(R.string.common_reason), ban.reason?.takeIf { it.isNotBlank() } ?: "-")
                                KeyValueRow(stringResource(R.string.bans_issued_by), "${ban.issuer ?: "-"} · ${Format.banExpiry(ban.issuanceTime)}")
                                KeyValueRow(stringResource(R.string.bans_expires), Format.banExpiry(ban.expires))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { revokeTarget = ban }) {
                                        Text(stringResource(R.string.bans_unban), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
        }

    if (showAdd) {
        PromptDialog(
            title = stringResource(R.string.bans_add_offline_ban),
            subtitle = stringResource(R.string.bans_ban_type_steam_by_account),
            confirmLabel = stringResource(R.string.common_ban),
            danger = true,
            fields = listOf(
                PromptField("user_id", stringResource(R.string.bans_userid_e_g_76561198_steam)),
                PromptField("original_name", stringResource(R.string.bans_player_nickname_optional)),
                PromptField("reason", stringResource(R.string.common_reason)),
                PromptField("duration", stringResource(R.string.common_duration_minutes_0_permanent), initial = "0", keyboardType = KeyboardType.Number),
                PromptField("ban_type", stringResource(R.string.bans_type_steam_ip), initial = "steam"),
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
                        snackbar.showOutcome(outcome, appString(R.string.bans_ban_added))
                        load()
                    }
                }
            },
            onDismiss = { showAdd = false },
        )
    }

    revokeTarget?.let { ban ->
        ConfirmDialog(
            title = stringResource(R.string.bans_unban_2, ban.originalName ?: ban.userId),
            text = stringResource(R.string.bans_type, ban.banType),
            confirmLabel = stringResource(R.string.bans_unban),
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
                    snackbar.showOutcome(outcome, appString(R.string.bans_unbanned))
                    load()
                }
            },
            onDismiss = { revokeTarget = null },
        )
    }
}
