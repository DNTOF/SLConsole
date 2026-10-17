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
            SectionCard("版本") {
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
                        InfoChip("非官方版本", color = MaterialTheme.colorScheme.error) { showSignature = true }
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
                        "SLConsole 是自由软件，依据 GNU 通用公共许可证第 3 版或更新版本发布，不附带任何担保。" +
                            "分发修改版时需要以同样的许可公开源代码，并保留版权声明；修改版请换用其他名称和图标。" +
                            "另有一项附加许可（链接例外），允许与 Microsoft Clarity SDK、Google Play Install Referrer 库一起分发，见 LICENSE-EXCEPTION.md。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip("源代码", color = MaterialTheme.colorScheme.primary) {
                            runCatching { uriHandler.openUri(SOURCE_URL) }
                        }
                        InfoChip("许可证全文", color = MaterialTheme.colorScheme.primary) {
                            runCatching { uriHandler.openUri(LICENSE_URL) }
                        }
                    }
                    Text(
                        "液态玻璃绘制使用 Backdrop 1.0.6（Apache-2.0）。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "匿名使用统计使用 Microsoft Clarity。默认开启；如果第一次打开时在引导里还没走到使用统计这一步就跳过，则会关闭，也可以在设置里改。开启后 Clarity 仍可能收到设备型号、系统版本、IP 地址和点击坐标。敏感内容会尽量遮住，但不是每一项都保证被遮住。" +
                            "检查更新会访问 GitHub API。除此之外不上传数据，服务器数据只发给你自己的服务器。",
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
