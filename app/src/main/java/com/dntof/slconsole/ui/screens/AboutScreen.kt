// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.data.local.SignatureCheck
import com.dntof.slconsole.ui.LocalReplayOnboarding
import com.dntof.slconsole.ui.components.AuthorCard
import com.dntof.slconsole.ui.components.GenuineBadge
import com.dntof.slconsole.ui.components.SignatureSheet
import com.dntof.slconsole.ui.components.decodeBadgePalette
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.withBottomChrome
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R

private const val SOURCE_URL = "https://github.com/DNTOF/SLConsole"
private const val LICENSE_URL = "https://github.com/DNTOF/SLConsole/blob/main/LICENSE"

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val report = remember { SignatureCheck.report(context) }
    // 徽标配色只能用官方证书指纹解出，两项都满足才显示徽标。
    val palette = remember(report) { if (report.mismatch == 0) decodeBadgePalette(report.key) else null }
    var showSignature by remember { mutableStateOf(false) }
    if (showSignature) {
        SignatureSheet(report = report, onDismiss = { showSignature = false })
    }
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.common_version)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "SLConsole ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (palette != null) {
                        Spacer(Modifier.weight(1f))
                        GenuineBadge(palette = palette, onClick = { showSignature = true })
                    } else {
                        InfoChip(stringResource(R.string.common_unofficial_build), color = MaterialTheme.colorScheme.error) { showSignature = true }
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.about_what_does)) {
                Text(
                    stringResource(R.string.about_see_your_phone_whether_server),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.about_adapted_plugin_actions_large_file),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { AuthorCard() }
        item {
            SectionCard(stringResource(R.string.about_special_thanks)) {
                Text(
                    stringResource(R.string.about_fxdyj_map_rendering_js),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard(stringResource(R.string.about_license)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InfoChip("GPL-3.0-or-later", color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Copyright (C) 2026 DNT_OF",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.about_slconsole_free_software_released_under),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip(stringResource(R.string.about_source_code), color = MaterialTheme.colorScheme.primary) {
                            runCatching { uriHandler.openUri(SOURCE_URL) }
                        }
                        InfoChip(stringResource(R.string.about_full_license), color = MaterialTheme.colorScheme.primary) {
                            runCatching { uriHandler.openUri(LICENSE_URL) }
                        }
                    }
                    Text(
                        stringResource(R.string.about_liquid_glass_rendering_uses_backdrop),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.about_anonymous_usage_statistics_use_microsoft),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.about_lora_poppins_fonts_interface_are),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            TextButton(onClick = LocalReplayOnboarding.current) {
                Text(stringResource(R.string.common_replay_onboarding))
            }
        }
    }
}
