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
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.components.ConfirmDialog
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.LabeledSwitch
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.rememberActiveServer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.ui.withBottomChrome
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString
import com.dntof.slconsole.ui.components.ButtonLabel

/** 远程控制:广播 / 管理聊天 / CASSIE / 回合与核弹(命令执行在"控制台"标签页)。 */
@Composable
fun RemoteScreen() {
    val server = rememberActiveServer()
    if (server == null) {
        EmptyState(Icons.Outlined.Terminal, stringResource(R.string.common_no_server_selected), stringResource(R.string.common_add_select_server_top_bar))
        return
    }

    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var broadcastText by rememberSaveable { mutableStateOf("") }
    var broadcastDuration by remember { mutableFloatStateOf(5f) }
    var clearPrevious by rememberSaveable { mutableStateOf(false) }

    var staffText by rememberSaveable { mutableStateOf("") }
    var staffSilent by rememberSaveable { mutableStateOf(false) }

    var cassieText by rememberSaveable { mutableStateOf("") }
    var cassieTranslation by rememberSaveable { mutableStateOf("") }
    var cassieHeld by rememberSaveable { mutableStateOf(false) }
    var cassieNoisy by rememberSaveable { mutableStateOf(true) }
    var cassieSubs by rememberSaveable { mutableStateOf(true) }

    var pendingRound by remember { mutableStateOf<String?>(null) }
    var pendingWarhead by remember { mutableStateOf<String?>(null) }

    fun send(path: String, body: kotlinx.serialization.json.JsonObject, okText: String) {
        scope.launch {
            snackbar.showOutcome(ControlRepository.call(server, path, body), okText)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.common_broadcast), subtitle = stringResource(R.string.remote_shown_center_every_players_screen)) {
                OutlinedTextField(
                    value = broadcastText,
                    onValueChange = { if (it.length <= 500) broadcastText = it },
                    label = { Text(stringResource(R.string.common_broadcast_message)) },
                    minLines = 2,
                    supportingText = { Text("${broadcastText.length}/500") },
                    modifier = Modifier.fillMaxWidth().keepAboveIme(),
                )
                Text(
                    stringResource(R.string.remote_duration, broadcastDuration.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = broadcastDuration,
                    onValueChange = { broadcastDuration = it },
                    valueRange = 1f..60f,
                )
                LabeledSwitch(stringResource(R.string.remote_clear_previous_broadcast), null, clearPrevious) { clearPrevious = it }
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = broadcastText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        send(
                            "/control/broadcast",
                            buildJsonObject {
                                put("message", broadcastText.trim())
                                put("duration_seconds", broadcastDuration)
                                put("clear_previous", clearPrevious)
                            },
                            appString(R.string.common_broadcast_sent),
                        )
                    },
                ) { Text(stringResource(R.string.remote_send_broadcast)) }
            }
        }
        item {
            SectionCard(stringResource(R.string.remote_admin_chat), subtitle = stringResource(R.string.remote_only_visible_players_adminchat_permission)) {
                OutlinedTextField(
                    value = staffText,
                    onValueChange = { if (it.length <= 500) staffText = it },
                    label = { Text(stringResource(R.string.remote_admin_chat_message)) },
                    minLines = 2,
                    supportingText = { Text("${staffText.length}/500") },
                    modifier = Modifier.fillMaxWidth().keepAboveIme(),
                )
                LabeledSwitch(stringResource(R.string.remote_send_silently), stringResource(R.string.remote_hide_sender), staffSilent) { staffSilent = it }
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = staffText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        send(
                            "/control/staffchat",
                            buildJsonObject {
                                put("message", staffText.trim())
                                put("is_silent", staffSilent)
                            },
                            appString(R.string.remote_sent_admin_chat),
                        )
                    },
                ) { Text(stringResource(R.string.remote_send_admin_chat)) }
            }
        }
        item {
            SectionCard(stringResource(R.string.remote_cassie_announcement), subtitle = stringResource(R.string.remote_announcements_use_uppercase_english_words)) {
                OutlinedTextField(
                    value = cassieText,
                    onValueChange = { if (it.length <= 500) cassieText = it },
                    label = { Text(stringResource(R.string.remote_announcement_text_english)) },
                    minLines = 2,
                    supportingText = { Text("${cassieText.length}/500") },
                    modifier = Modifier.fillMaxWidth().keepAboveIme(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = cassieTranslation,
                    onValueChange = { if (it.length <= 500) cassieTranslation = it },
                    label = { Text(stringResource(R.string.remote_subtitle_translation_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().keepAboveIme(),
                )
                LabeledSwitch(stringResource(R.string.remote_hold_announcement_wait_confirmation), null, cassieHeld) { cassieHeld = it }
                LabeledSwitch(stringResource(R.string.remote_include_ambient_noise), null, cassieNoisy) { cassieNoisy = it }
                LabeledSwitch(stringResource(R.string.remote_show_subtitles), null, cassieSubs) { cassieSubs = it }
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = cassieText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        send(
                            "/control/cassie",
                            buildJsonObject {
                                put("message", cassieText.trim())
                                put("isHeld", cassieHeld)
                                put("isNoisy", cassieNoisy)
                                put("isSubtitles", cassieSubs)
                                if (cassieTranslation.isNotBlank()) put("translation", cassieTranslation.trim())
                            },
                            appString(R.string.remote_cassie_announcement_sent),
                        )
                    },
                ) { Text(stringResource(R.string.remote_send_cassie)) }
            }
        }
        item {
            SectionCard(stringResource(R.string.remote_round_warhead), subtitle = stringResource(R.string.remote_dangerous_actions_require_confirmation)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingRound = "restart" }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.common_restart_round)) }
                    OutlinedButton(onClick = { pendingRound = "end" }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.remote_end_round)) }
                    OutlinedButton(onClick = { pendingRound = "start" }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.remote_start_round)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingWarhead = "start" }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.remote_start_warhead)) }
                    OutlinedButton(onClick = { pendingWarhead = "stop" }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.common_stop)) }
                    Button(onClick = { pendingWarhead = "detonate" }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.remote_detonate)) }
                }
            }
        }
    }

    pendingRound?.let { action ->
        val (label, title, done) = when (action) {
            "restart" -> Triple(R.string.remote_round_restart, R.string.remote_round_restart_title, R.string.remote_round_restarted)
            "end" -> Triple(R.string.remote_round_end, R.string.remote_round_end_title, R.string.remote_round_ended)
            else -> Triple(R.string.remote_round_start, R.string.remote_round_start_title, R.string.remote_round_started)
        }
        val doneText = stringResource(done)
        ConfirmDialog(
            title = stringResource(title),
            danger = action != "start",
            confirmLabel = stringResource(label),
            onConfirm = {
                send("/control/round", buildJsonObject { put("action", action) }, doneText)
            },
            onDismiss = { pendingRound = null },
        )
    }
    pendingWarhead?.let { action ->
        val (label, title) = when (action) {
            "start" -> R.string.remote_warhead_start to R.string.remote_warhead_start_title
            "stop" -> R.string.remote_warhead_stop to R.string.remote_warhead_stop_title
            else -> R.string.remote_warhead_detonate to R.string.remote_warhead_detonate_title
        }
        ConfirmDialog(
            title = stringResource(title),
            text = if (action == "detonate") stringResource(R.string.remote_warhead_will_detonate_immediately_cant) else null,
            danger = action == "detonate",
            confirmLabel = stringResource(label),
            onConfirm = {
                send("/control/round/warhead", buildJsonObject { put("action", action) }, appString(R.string.remote_warhead_command_executed))
            },
            onDismiss = { pendingWarhead = null },
        )
    }
}
