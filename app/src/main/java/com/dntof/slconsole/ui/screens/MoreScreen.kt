package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.ui.Routes

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val entries = listOf(
        Triple(Routes.REMOTE, Icons.Outlined.SettingsRemote, "远程控制"),
        Triple(Routes.VOICE, Icons.Outlined.GraphicEq, "语音监听"),
        Triple(Routes.BANS, Icons.Outlined.Gavel, "封禁管理"),
        Triple(Routes.LOGS, Icons.Outlined.Article, "服务器日志"),
        Triple(Routes.AUDIT, Icons.Outlined.FactCheck, "控制审计"),
        Triple(Routes.PLUGINS, Icons.Outlined.Extension, "插件管理"),
        Triple(Routes.FILES, Icons.Outlined.Folder, "文件管理"),
        Triple(Routes.REPORTS, Icons.Outlined.Flag, "举报管理"),
        Triple(Routes.SERVERS, Icons.Outlined.Dns, "服务器管理"),
        Triple(Routes.ABOUT, Icons.Outlined.Info, "关于"),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(entries.size) { index ->
            val (route, icon, label) = entries[index]
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { Icon(icon, null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.clickable { onNavigate(route) },
            )
            if (index < entries.lastIndex) HorizontalDivider()
        }
        item {
            Text(
                "SLConsole ${BuildConfig.VERSION_NAME} · Foundation Console 移动端",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
