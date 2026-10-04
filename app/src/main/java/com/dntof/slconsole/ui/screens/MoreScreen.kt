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

private data class HubEntry(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private data class HubGroup(val title: String, val entries: List<HubEntry>)

private val HUB_GROUPS = listOf(
    HubGroup(
        "监控",
        listOf(
            HubEntry(Routes.EVENTS, "实时动态", "玩家进出、死亡与回合事件", Icons.Outlined.Bolt),
            HubEntry(Routes.VOICE, "语音监听", "收听对讲,查看正在说话的人", Icons.Outlined.GraphicEq),
            HubEntry(Routes.MAPS, "地图", "种子重建、定位与设施控制", Icons.Outlined.Map),
        ),
    ),
    HubGroup(
        "管控",
        listOf(
            HubEntry(Routes.REMOTE, "远程控制", "广播、CASSIE、回合与核弹", Icons.Outlined.SettingsRemote),
            HubEntry(Routes.BANS, "封禁管理", "封禁列表、离线封禁与解封", Icons.Outlined.Gavel),
            HubEntry(Routes.REPORTS, "举报管理", "待处理举报与处理", Icons.Outlined.Flag),
        ),
    ),
    HubGroup(
        "运维",
        listOf(
            HubEntry(Routes.LOGS, "服务器日志", "尾部读取与关键字过滤", Icons.AutoMirrored.Outlined.Article),
            HubEntry(Routes.AUDIT, "控制审计", "查看操作记录", Icons.AutoMirrored.Outlined.FactCheck),
            HubEntry(Routes.PLUGINS, "插件管理", "EXILED / LabAPI 启停重载", Icons.Outlined.Extension),
            HubEntry(Routes.ADAPTED, "适配插件", "SLPlayer 控制,OmegaWarhead 状态", Icons.Outlined.LibraryMusic),
            HubEntry(Routes.FILES, "文件管理", "浏览并编辑 FileRoot", Icons.Outlined.Folder),
        ),
    ),
    HubGroup(
        "应用",
        listOf(
            HubEntry(Routes.SERVERS, "服务器管理", "切换、添加与删除连接", Icons.Outlined.Dns),
            HubEntry(Routes.SETTINGS, "设置", "外观、隐私与解锁", Icons.Outlined.Settings),
            HubEntry(Routes.ABOUT, "关于", "版本、作者与致谢", Icons.Outlined.Info),
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
            Text("中心", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "监控、管控、运维和应用分开放置,底栏里的功能这里也能进。",
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
            group.title,
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
                        title = entry.title,
                        subtitle = entry.subtitle,
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
