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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.ui.Routes
import com.dntof.slconsole.ui.withBottomChrome
import com.dntof.slconsole.ui.components.AppLayout
import com.dntof.slconsole.ui.components.FeatureTile
import com.dntof.slconsole.ui.components.LocalAppLayout
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import androidx.annotation.StringRes

private data class HubEntry(
    val route: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
)

private data class HubGroup(@StringRes val title: Int, val entries: List<HubEntry>)

private val HUB_GROUPS = listOf(
    HubGroup(
        R.string.more_monitoring,
        listOf(
            HubEntry(Routes.EVENTS, R.string.common_live_events, R.string.more_joins_leaves_deaths_round_events, Icons.Outlined.Bolt),
            HubEntry(Routes.VOICE, R.string.common_voice_monitor, R.string.more_listen_radio_chatter_see_whos, Icons.Outlined.GraphicEq),
            HubEntry(Routes.MAPS, R.string.common_map, R.string.more_seed_rebuild_locations_facility_control, Icons.Outlined.Map),
        ),
    ),
    HubGroup(
        R.string.more_management,
        listOf(
            HubEntry(Routes.REMOTE, R.string.common_remote_control, R.string.more_broadcasts_cassie_rounds_warhead, Icons.Outlined.SettingsRemote),
            HubEntry(Routes.BANS, R.string.common_bans, R.string.more_ban_list_offline_bans_unbans, Icons.Outlined.Gavel),
            HubEntry(Routes.REPORTS, R.string.common_reports, R.string.more_pending_reports_handling, Icons.Outlined.Flag),
        ),
    ),
    HubGroup(
        R.string.more_operations,
        listOf(
            HubEntry(Routes.LOGS, R.string.common_server_logs, R.string.more_tail_reading_keyword_filtering, Icons.AutoMirrored.Outlined.Article),
            HubEntry(Routes.AUDIT, R.string.common_control_audit, R.string.more_view_operation_log, Icons.AutoMirrored.Outlined.FactCheck),
            HubEntry(Routes.PLUGINS, R.string.common_plugins, R.string.more_exiled_labapi_enable_disable_reload, Icons.Outlined.Extension),
            HubEntry(Routes.ADAPTED, R.string.common_adapted_plugins, R.string.more_slplayer_control_omegawarhead_status, Icons.Outlined.LibraryMusic),
            HubEntry(Routes.FILES, R.string.common_files, R.string.more_browse_edit_fileroot, Icons.Outlined.Folder),
        ),
    ),
    HubGroup(
        R.string.more_app,
        listOf(
            HubEntry(Routes.SERVERS, R.string.common_servers, R.string.more_switch_add_remove_connections, Icons.Outlined.Dns),
            HubEntry(Routes.SETTINGS, R.string.common_settings, R.string.more_appearance_privacy_unlock, Icons.Outlined.Settings),
            HubEntry(Routes.ABOUT, R.string.common_about, R.string.more_version_author_credits, Icons.Outlined.Info),
        ),
    ),
)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val columns = if (LocalAppLayout.current == AppLayout.Compact) 2 else 3
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.common_hub), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.more_monitoring_management_operations_app_settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HUB_GROUPS.forEach { group ->
            item {
                HubGroupSection(group, columns, onNavigate)
            }
        }
        item {
            Text(
                "SLConsole ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HubGroupSection(group: HubGroup, columns: Int, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(group.title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        group.entries.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { entry ->
                    FeatureTile(
                        icon = entry.icon,
                        title = stringResource(entry.title),
                        subtitle = stringResource(entry.subtitle),
                        modifier = Modifier.weight(1f).height(132.dp),
                        onClick = { onNavigate(entry.route) },
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
