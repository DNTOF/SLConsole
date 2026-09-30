package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.ui.components.KeyValueRow
import com.dntof.slconsole.ui.components.SectionCard

@Composable
fun AboutScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("SLConsole") {
                Text(
                    "面向 SCP: Secret Laboratory 服务器管理员的移动端管理 + 监控工具。" +
                        "直连游戏服务器上的 SLDataAPI 插件,不依赖任何中间平台。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard("对接契约") {
                KeyValueRow("插件", "SLDataAPI 2.6.0(PEAK)")
                KeyValueRow("数据面", "GET /get_sl_data(Bearer VerifyToken)")
                KeyValueRow("控制面", "POST /control/*(Bearer API Key)")
                KeyValueRow("实时通道", "WS /control(hello/ping/call/event)")
                KeyValueRow("默认端口", "8081(语音 8082,未在本版实现)")
            }
        }
        item {
            SectionCard("功能范围") {
                Text(
                    "已实现:状态监控、玩家管理、广播 / 管理聊天 / CASSIE、" +
                        "回合与核弹控制、控制台命令、实时事件流、封禁管理、" +
                        "服务器日志、控制审计、插件管理、多服务器管理、" +
                        "地图视图(种子本地重建 + 玩家定位 + 灯光/门/电梯控制)、" +
                        "语音监听、文件管理、举报管理、外观(液态玻璃)。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard("安全提示") {
                Text(
                    "1. SLDataAPI 的 HTTP/WS 为明文传输,建议仅在可信内网或 TLS 反向代理后使用。\n" +
                        "2. API Key 权限请按需分配(sld_duty_ 为只读值班模板,无法执行控制)。\n" +
                        "3. 本机凭据经 AndroidKeyStore AES-256-GCM 加密存储,卸载应用后无法恢复。\n" +
                        "4. 敏感操作(封禁、引爆、重启回合)均有二次确认。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
