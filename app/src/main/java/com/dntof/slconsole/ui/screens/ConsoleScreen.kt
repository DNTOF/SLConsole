package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CardDefaults
import com.dntof.slconsole.data.model.ConsoleOutputData
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalTopChrome
import com.dntof.slconsole.ui.belowTopBar
import com.dntof.slconsole.ui.bottomChromePadding
import com.dntof.slconsole.ui.keepAboveIme
import com.microsoft.clarity.modifiers.clarityMask
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.rememberActiveServer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TERMINAL_BG = 0xFF0B0E13
private const val TERMINAL_TEXT = 0xFFD8DEE9
private const val TERMINAL_PROMPT = 0xFFFF9EC6
private const val MAX_TRANSCRIPT = 12000

@Composable
fun ConsoleScreen() {
    val server = rememberActiveServer()

    if (server == null) {
        EmptyState(Icons.Outlined.Terminal, "未选择服务器", "先在顶栏添加或选择一个服务器")
        return
    }
    if (!server.hasControl) {
        Column(Modifier.fillMaxSize().belowTopBar().padding(16.dp).bottomChromePadding()) {
            SectionCard("控制台", subtitle = "需要控制面 API Key") {
                Text(
                    "控制台通过 API Key 执行服务器命令。请在服务器设置中配置 API Key 后重试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    var command by rememberSaveable { mutableStateOf("") }
    var transcript by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    // 新输出到达时滚到底部
    LaunchedEffect(transcript) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun exec() {
        val cmd = command.trim()
        if (cmd.isEmpty() || running) return
        running = true
        command = ""
        scope.launch {
            val outcome = ControlRepository.call(
                server,
                "/control/console/command",
                buildJsonObject { put("command", cmd) },
            )
            transcript = (transcript + when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    val parsed = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(ConsoleOutputData.serializer(), it) }.getOrNull()
                    }
                    val out = parsed?.output ?: parsed?.console ?: outcome.message ?: "(无输出)"
                    "❯ $cmd\n$out\n\n"
                }
                is ControlRepository.ControlOutcome.Failure -> "❯ $cmd\n✗ ${outcome.message}\n\n"
            }).takeLast(MAX_TRANSCRIPT)
            running = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = LocalTopChrome.current)
            .bottomChromePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 终端面板从顶栏下沿开始,静止时黑底不会铺进顶栏
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clarityMask(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = Color(TERMINAL_BG)),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "● ● ●",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF5A6472),
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${server.addressText} — 控制台",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B95A3),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        transcript.ifBlank {
                            "SLConsole 终端已就绪(连接:${server.addressText})\n" +
                                "输入命令执行,输出实时追加到这里。\n" +
                                "提示:sldataapi 命令被服务器拒绝。\n"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        ),
                        color = Color(TERMINAL_TEXT),
                    )
                }
            }
        }

        // 输入行
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "❯",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp),
            )
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                placeholder = { Text("输入命令,如:list / help", fontFamily = FontFamily.Monospace) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { exec() }),
                modifier = Modifier.weight(1f).keepAboveIme(),
            )
            IconButton(onClick = { exec() }, enabled = command.isNotBlank() && !running) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "执行",
                    tint = if (command.isNotBlank() && !running) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
