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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.analytics.ClarityDefaults
import com.dntof.slconsole.analytics.UsageAnalytics
import androidx.compose.material3.TextButton
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("外观", style = MaterialTheme.typography.headlineSmall)
            Text(
                "只影响显示,不改变监控和控制功能。",
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
            Text("隐私", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            SectionCard("使用统计", subtitle = "默认开启") {
                LabeledSwitch(
                    title = "帮助改进（匿名使用统计）",
                    subtitle = "匿名记录界面怎么被使用，用来改进应用。不记录服务器地址、密钥和控制台内容。关闭后立即停止，下次打开也不再收集。",
                    checked = analytics,
                    onCheckedChange = { enabled ->
                        UsageAnalytics.setEnabled(context, enabled)
                        scope.launch { ServiceLocator.settingsStore.setUsageAnalytics(enabled) }
                    },
                )
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
