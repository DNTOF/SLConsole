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
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.ui.components.LabeledSwitch
import com.dntof.slconsole.ui.components.LocalLiquidGlass
import com.dntof.slconsole.ui.components.SectionCard
import kotlinx.coroutines.launch
import com.dntof.slconsole.ui.withBottomChrome

@Composable
fun SettingsScreen() {
    val glass by ServiceLocator.settingsStore.liquidGlassFlow.collectAsState(initial = LocalLiquidGlass.current)
    val scope = rememberCoroutineScope()
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
    }
}
