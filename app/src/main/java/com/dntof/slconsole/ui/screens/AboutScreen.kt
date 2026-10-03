package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.data.local.SignatureCheck
import com.dntof.slconsole.ui.components.AuthorCard
import com.dntof.slconsole.ui.components.InfoChip
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.withBottomChrome

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val official = remember { SignatureCheck.isOfficial(context) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp).withBottomChrome(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("版本") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "SLConsole ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (official) {
                        InfoChip("官方签名", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        InfoChip("非官方版本", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            SectionCard("功能范围") {
                Text(
                    "你可以在手机上查看服务器是否在线、这一局进行到哪一步、现在有哪些玩家。" +
                        "也可以输入控制台命令，打开地图，听语音，处理封禁和举报，管理插件，以及查看、修改服务器上的文件。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { AuthorCard() }
        item {
            SectionCard("特别鸣谢") {
                Text("FXDYJ", style = MaterialTheme.typography.titleMedium)
                Text(
                    "提供地图渲染 JS。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard("许可") {
                Text(
                    "液态玻璃绘制使用 Backdrop 1.0.6（Apache-2.0）。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
