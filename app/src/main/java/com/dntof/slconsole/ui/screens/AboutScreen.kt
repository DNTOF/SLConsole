package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.data.local.SignatureCheck
import com.dntof.slconsole.ui.LocalReplayOnboarding
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
                Text(
                    "SLDataAPI 2.6.1 预发布里的适配插件动作和大文件分块，只有服务器打开对应开关，并且控制连接的 hello 里带有 beta 时才会出现。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { AuthorCard() }
        item {
            SectionCard("特别鸣谢") {
                Text(
                    "FXDYJ（提供地图渲染JS）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard("许可") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "液态玻璃绘制使用 Backdrop 1.0.6（Apache-2.0）。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "匿名使用统计使用 Microsoft Clarity SDK。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "界面里的 Lora、Poppins 字体以 SIL Open Font License 提供。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            TextButton(onClick = LocalReplayOnboarding.current) {
                Text("重新查看新手引导")
            }
        }
    }
}
