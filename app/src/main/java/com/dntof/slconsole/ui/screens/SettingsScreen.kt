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
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            Text(
                "外观、隐私和解锁方式，只在这台设备上生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SectionCard("液态玻璃", subtitle = "默认关闭") {
                LabeledSwitch(
                    title = "启用液态玻璃",
                    subtitle = "为导航和卡片加上磨砂与高光。\n关闭后恢复纯色,重启后仍会保持。",
                    checked = glass,
                    onCheckedChange = { enabled ->
                        scope.launch { ServiceLocator.settingsStore.setLiquidGlass(enabled) }
                    },
                )
                Text(
                    "Android 12 及以上会实时模糊背后正在滚动的内容;更低版本改为半透明。文字仍然可读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            SectionCard("检查更新", subtitle = "当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）") {
                LabeledSwitch(
                    title = "自动检查更新",
                    subtitle = "启动时请求 GitHub API（api.github.com）查看有没有新版本，最多每 6 小时一次。只读取公开的发布信息，不发送服务器信息或个人数据。",
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
                        if (lastCheck > 0) "上次检查：${formatCheckTime(lastCheck)}" else "还没有检查过",
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
                                    Text("检查中")
                                }
                            } else {
                                Text("立即检查")
                            }
                        }
                    }
                }
                ignoredVersion?.let { version ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已忽略 $version",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            scope.launch { ServiceLocator.settingsStore.setIgnoredUpdateVersion(null) }
                        }) { Text("取消忽略") }
                    }
                }
            }
        }
        item {
            Text("隐私", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            SectionCard("使用统计", subtitle = "默认开启") {
                LabeledSwitch(
                    title = "帮助改进（匿名使用统计）",
                    subtitle = "用 Microsoft Clarity 匿名记录界面怎么被使用，用来改进应用。服务器地址、密钥、输入框、控制台内容和玩家昵称会被遮住。关闭后立即停止，下次打开也不再收集。",
                    checked = analytics,
                    onCheckedChange = { enabled ->
                        UsageAnalytics.setEnabled(context, enabled)
                        scope.launch { ServiceLocator.settingsStore.setUsageAnalytics(enabled) }
                    },
                )
            }
        }
        item {
            SectionCard("生物识别解锁", subtitle = "默认关闭") {
                LabeledSwitch(
                    title = "生物识别解锁",
                    subtitle = "打开后，启动应用或离开超过 30 秒再回来时，要先用指纹、面容或锁屏密码解锁。",
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
                        AppLock.authenticate(context, title = "开启生物识别解锁") { result ->
                            lockHint = when (result) {
                                AppLock.Result.Success -> {
                                    AppLock.markUnlocked()
                                    scope.launch { ServiceLocator.settingsStore.setBiometricLock(true) }
                                    null
                                }
                                AppLock.Result.Cancelled -> "已取消，没有开启。"
                                AppLock.Result.Unavailable -> AppLock.unavailableText(AppLock.availability(context))
                                is AppLock.Result.Error -> "没有开启：${result.message}"
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
            SectionCard("新手引导") {
                Text(
                    "再看一遍欢迎、添加服务器和功能介绍。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = LocalReplayOnboarding.current) {
                    Text("重新查看新手引导")
                }
            }
        }
    }
}

private fun formatCheckTime(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(time))
