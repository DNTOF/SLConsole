// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.analytics.ClarityDefaults
import com.dntof.slconsole.analytics.UsageAnalytics
import com.dntof.slconsole.security.AppLock
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.Alignment
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.data.update.UpdateChecker
import com.dntof.slconsole.ui.LocalSnackbarHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dntof.slconsole.ui.LocalReplayOnboarding
import com.dntof.slconsole.ui.components.LabeledSwitch
import com.dntof.slconsole.ui.components.LocalLiquidGlass
import com.dntof.slconsole.ui.components.SectionCard
import kotlinx.coroutines.launch
import com.dntof.slconsole.ui.withBottomChrome
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

@Composable
fun SettingsScreen() {
    val glass by ServiceLocator.settingsStore.liquidGlassFlow.collectAsState(initial = LocalLiquidGlass.current)
    val analytics by ServiceLocator.settingsStore.usageAnalyticsFlow.collectAsState(initial = ClarityDefaults.ENABLED)
    val biometricLock by ServiceLocator.settingsStore.biometricLockFlow.collectAsState(initial = false)
    var lockHint by remember { mutableStateOf<String?>(null) }
    val autoUpdate by ServiceLocator.settingsStore.autoUpdateCheckFlow.collectAsState(initial = true)
    val lastCheck by ServiceLocator.settingsStore.lastUpdateCheckFlow.collectAsState(initial = 0L)
    val ignoredVersion by ServiceLocator.settingsStore.ignoredUpdateVersionFlow.collectAsState(initial = null)
    val checking by UpdateChecker.checking.collectAsState()
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.common_settings), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.settings_appearance_privacy_unlock_options_apply),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SectionCard(stringResource(R.string.settings_liquid_glass), subtitle = stringResource(R.string.settings_off_by_default)) {
                LabeledSwitch(
                    title = stringResource(R.string.settings_enable_liquid_glass),
                    subtitle = stringResource(R.string.settings_adds_frosted_blur_highlights_navigation),
                    checked = glass,
                    onCheckedChange = { enabled ->
                        scope.launch { ServiceLocator.settingsStore.setLiquidGlass(enabled) }
                    },
                )
                Text(
                    stringResource(R.string.settings_android_12_later_content_scrolling),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            SectionCard(stringResource(R.string.settings_check_updates), subtitle = stringResource(R.string.settings_current_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) {
                LabeledSwitch(
                    title = stringResource(R.string.settings_check_updates_automatically),
                    subtitle = stringResource(R.string.settings_launch_queries_github_api_api),
                    checked = autoUpdate,
                    onCheckedChange = { enabled ->
                        scope.launch { ServiceLocator.settingsStore.setAutoUpdateCheck(enabled) }
                    },
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (lastCheck > 0) stringResource(R.string.settings_last_checked, formatCheckTime(lastCheck)) else stringResource(R.string.settings_never_checked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        enabled = !checking,
                        onClick = {
                            scope.launch {
                                val outcome = UpdateChecker.check(ServiceLocator.settingsStore, manual = true)
                                UpdateChecker.describe(outcome)?.let { snackbar.showSnackbar(it, withDismissAction = true) }
                            }
                        },
                    ) {
                        AnimatedContent(
                            targetState = checking,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "checkButton",
                        ) { busy ->
                            if (busy) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_checking))
                                }
                            } else {
                                Text(stringResource(R.string.settings_check_now))
                            }
                        }
                    }
                }
                ignoredVersion?.let { version ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_ignored, version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            scope.launch { ServiceLocator.settingsStore.setIgnoredUpdateVersion(null) }
                        }) { Text(stringResource(R.string.settings_unignore)) }
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            SectionCard(stringResource(R.string.common_usage_statistics), subtitle = stringResource(R.string.settings_by_default)) {
                LabeledSwitch(
                    title = stringResource(R.string.settings_help_improve_anonymous_usage_statistics),
                    subtitle = stringResource(R.string.settings_uses_microsoft_clarity_record_how),
                    checked = analytics,
                    onCheckedChange = { enabled ->
                        UsageAnalytics.setEnabled(context, enabled)
                        scope.launch { ServiceLocator.settingsStore.setUsageAnalytics(enabled) }
                    },
                )
            }
        }
        item {
            SectionCard(stringResource(R.string.common_biometric_unlock), subtitle = stringResource(R.string.settings_off_by_default)) {
                LabeledSwitch(
                    title = stringResource(R.string.common_biometric_unlock),
                    subtitle = stringResource(R.string.settings_when_need_unlock_fingerprint_face),
                    checked = biometricLock,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            lockHint = null
                            scope.launch { ServiceLocator.settingsStore.setBiometricLock(false) }
                            return@LabeledSwitch
                        }
                        val availability = AppLock.availability(context)
                        if (availability != AppLock.Availability.Ready) {
                            lockHint = AppLock.unavailableText(availability)
                            return@LabeledSwitch
                        }
                        // 先认证成功一次再保存，免得打开后自己解不开。
                        AppLock.authenticate(context, title = appString(R.string.common_turn_biometric_unlock)) { result ->
                            lockHint = when (result) {
                                AppLock.Result.Success -> {
                                    AppLock.markUnlocked()
                                    scope.launch { ServiceLocator.settingsStore.setBiometricLock(true) }
                                    null
                                }
                                AppLock.Result.Cancelled -> appString(R.string.common_cancelled_not_turned)
                                AppLock.Result.Unavailable -> AppLock.unavailableText(AppLock.availability(context))
                                is AppLock.Result.Error -> appString(R.string.common_not_turned, result.message)
                            }
                        }
                    },
                )
                lockHint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.common_onboarding)) {
                Text(
                    stringResource(R.string.settings_see_welcome_server_setup_feature),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = LocalReplayOnboarding.current) {
                    Text(stringResource(R.string.common_replay_onboarding))
                }
            }
        }
    }
}

private fun formatCheckTime(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(time))
