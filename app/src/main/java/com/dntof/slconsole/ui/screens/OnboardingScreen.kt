// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.analytics.ClarityDefaults
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.SlHttpClient
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalImeLift
import com.dntof.slconsole.security.AppLock
import com.dntof.slconsole.ui.components.AppIconBadge
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.util.stripRichText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

private const val STEP_COUNT = 6

private data class TourCard(val title: String, val body: String, val icon: ImageVector)

private val TOUR = listOf(
    TourCard(
        "概览",
        "底栏第一项。看服务器是否在线、这一局进行到哪，以及现在有多少人。",
        Icons.Outlined.SpaceDashboard,
    ),
    TourCard(
        "玩家",
        "底栏第二项。可以搜索在线玩家，也可以踢出、封禁或发私信。",
        Icons.Outlined.Groups,
    ),
    TourCard(
        "控制台",
        "底栏第三项。把命令直接发到服务器，回显会留在下面。",
        Icons.Outlined.Terminal,
    ),
    TourCard(
        "地图",
        "底栏第四项。用这一局的种子在手机上画出设施，并标出玩家位置。",
        Icons.Outlined.Map,
    ),
    TourCard(
        "中心",
        "底栏最后一项。适配插件、文件、语音和封禁都从这里进。设置里可以打开液态玻璃。",
        Icons.Outlined.Apps,
    ),
)

/**
 * 第一次打开时盖在主界面上。用普通纯色表面，不跟液态玻璃开关走。
 * 「跳过」只结束引导，不改使用统计的当前值。
 */
@Composable
fun OnboardingScreen(replay: Boolean, onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = ServiceLocator.serverStore
    val settings = ServiceLocator.settingsStore
    val storedAnalytics by settings.usageAnalyticsFlow.collectAsState(initial = ClarityDefaults.ENABLED)
    // 切换深浅色或旋转会重建界面，步骤和填了一半的地址要留住。密钥不放进 saved state。
    var step by rememberSaveable { mutableIntStateOf(0) }
    var finishing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(ServerConfig.DEFAULT_PORT.toString()) }
    var verifyToken by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var analyticsChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val analyticsSelected = analyticsChoice ?: if (replay) storedAnalytics else null
    val storedLock by settings.biometricLockFlow.collectAsState(initial = false)
    var lockChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // 用户可能中途去系统设置录指纹，回来时重新查一次。
    var lockAvailability by remember { mutableStateOf(AppLock.availability(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lockAvailability = AppLock.availability(context) }

    // 欢迎页入场：只在第一次显示时播一次。系统关闭动画时直接停在最终状态。
    val reduceMotion = rememberReduceMotion()
    val entrance = remember { Animatable(if (reduceMotion || step != 0) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (entrance.value < 1f) entrance.animateTo(1f, tween(durationMillis = 1300, easing = LinearEasing))
    }
    val entranceProgress = { entrance.value }
    // 背景相位只在欢迎页组合，离开这一步无限动画就停掉。
    val welcomePhase: State<Float>? = if (step == 0) rememberWelcomePhase(reduceMotion) else null

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            settings.setOnboardingCompleted(true)
            onFinished()
        }
    }

    fun goNext() {
        if (step != 1) {
            if (step >= STEP_COUNT - 1) finish() else step += 1
            return
        }
        if (saving) return
        saving = true
        scope.launch {
            val error = saveQuickServer(
                store = store,
                draftId = draftId,
                host = host,
                port = port,
                verifyToken = verifyToken,
                apiKey = apiKey,
                onDraftId = { draftId = it },
            )
            formError = error
            saving = false
            if (error == null) step += 1
        }
    }

    // 系统返回键回到上一步，第一步时交给外层处理。
    BackHandler(enabled = step > 0 && !finishing) { step -= 1 }

    val ime = LocalImeLift.current.overlap
    // 用 Surface 提供 onBackground 作为内容色，深色模式下正文才不会是黑字。
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            welcomePhase?.let { WelcomeBackdrop(it, Modifier.fillMaxSize()) }
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "新手引导",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = if (step == 0) Modifier.staggerIn(entranceProgress, 0) else Modifier,
                    )
                    when (step) {
                        0 -> WelcomeStep(welcomePhase, entranceProgress)
                        1 -> AddServerStep(
                            host = host,
                            port = port,
                            verifyToken = verifyToken,
                            apiKey = apiKey,
                            testing = testing,
                            testResult = testResult,
                            formError = formError,
                            onHost = { host = it },
                            onPort = { port = it },
                            onVerify = { verifyToken = it },
                            onApiKey = { apiKey = it },
                            onTest = {
                                val h = host.trim()
                                val p = port.toIntOrNull() ?: ServerConfig.DEFAULT_PORT
                                if (h.isEmpty() || verifyToken.isBlank()) {
                                    testResult = false to "请先填写主机地址和 VerifyToken"
                                    return@AddServerStep
                                }
                                testing = true
                                testResult = null
                                scope.launch {
                                    val temp = ServerConfig(id = "test", host = h, port = p, verifyToken = verifyToken)
                                    testResult = when (val result = SlHttpClient().getData(temp, "/get_sl_data")) {
                                        is SlHttpClient.HttpResult.Success -> {
                                            val name = (result.body["server_name"] as? JsonPrimitive)?.content?.let { stripRichText(it) }
                                            val count = (result.body["players_count"] as? JsonPrimitive)?.content
                                            true to "连接成功：${name ?: "未命名服务器"}（在线 ${count ?: "?"} 人）"
                                        }
                                        is SlHttpClient.HttpResult.Failure -> false to result.message
                                    }
                                    testing = false
                                }
                            },
                        )
                        2 -> TourStep()
                        3 -> ConsentStep(
                            selected = analyticsSelected,
                            onPick = { enabled ->
                                analyticsChoice = enabled
                                scope.launch { settings.setUsageAnalytics(enabled) }
                            },
                        )
                        4 -> BiometricStep(
                            availability = lockAvailability,
                            selected = lockChoice ?: if (replay || storedLock) storedLock else null,
                            message = lockMessage,
                            onEnable = {
                                lockMessage = null
                                AppLock.authenticate(context, title = "开启生物识别解锁") { result ->
                                    when (result) {
                                        AppLock.Result.Success -> {
                                            AppLock.markUnlocked()
                                            lockChoice = true
                                            scope.launch { settings.setBiometricLock(true) }
                                        }
                                        AppLock.Result.Cancelled -> lockMessage = "已取消，没有开启。"
                                        AppLock.Result.Unavailable -> lockMessage = "现在不能使用生物识别或锁屏密码。"
                                        is AppLock.Result.Error -> lockMessage = "没有开启：${result.message}"
                                    }
                                }
                            },
                            onDecline = {
                                lockMessage = null
                                lockChoice = false
                                scope.launch { settings.setBiometricLock(false) }
                            },
                        )
                        else -> DoneStep()
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .staggerIn(entranceProgress, 4)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp + ime),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StepDots(step)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { finish() }, enabled = !finishing) { Text("跳过") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { if (step > 0) step -= 1 }, enabled = step > 0 && !finishing) {
                            Text("上一步")
                        }
                        Button(onClick = { goNext() }, enabled = !finishing && !testing && !saving) {
                            Text(if (step >= STEP_COUNT - 1) "完成" else "下一步")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeStep(phase: State<Float>?, progress: () -> Float) {
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .staggerIn(progress, 0, distance = 24.dp)
            .then(if (phase != null) Modifier.logoGlow(phase, glowColor, 76.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIconBadge(size = 84.dp)
    }
    Text(
        "欢迎使用 SLConsole",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.staggerIn(progress, 1),
    )
    Text(
        "这是用来查看和管理 SCP:SL 服务器的手机客户端。它直接连接你服务器上的 SLDataAPI，地址和密钥只保存在这台手机上。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.staggerIn(progress, 2),
    )
    FlowRow(
        modifier = Modifier.staggerIn(progress, 3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("实时监控", "远程控制", "地图与语音", "文件与插件").forEach { label ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun BiometricStep(
    availability: AppLock.Availability,
    selected: Boolean?,
    message: String?,
    onEnable: () -> Unit,
    onDecline: () -> Unit,
) {
    Text("生物识别解锁", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "打开后，启动应用或离开超过 30 秒再回来时，要先用指纹、面容或锁屏密码解锁。默认关闭，以后也可以在设置里改。",
        style = MaterialTheme.typography.bodyLarge,
    )
    if (availability != AppLock.Availability.Ready) {
        Text(
            "暂不可用：${AppLock.unavailableText(availability)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected == true) {
            Button(onClick = onEnable, modifier = Modifier.weight(1f)) { Text("开启") }
        } else {
            OutlinedButton(onClick = onEnable, modifier = Modifier.weight(1f)) { Text("开启") }
        }
        if (selected == false) {
            Button(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("暂不") }
        } else {
            OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("暂不") }
        }
    }
    when {
        message != null -> Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        selected == true -> Text("已开启。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        selected == false -> Text("先不开启。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddServerStep(
    host: String,
    port: String,
    verifyToken: String,
    apiKey: String,
    testing: Boolean,
    testResult: Pair<Boolean, String>?,
    formError: String?,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onVerify: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onTest: () -> Unit,
) {
    Text("添加一台服务器", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "这一步可以留空。主机和 VerifyToken 都填了的话，点下一步会保存，并把它设为当前服务器。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "verify_token 用来读取监控数据，写在插件的 config.yml 里。API Key 用来执行控制，从 SLDataAPI 2.6 开始使用，在游戏控制台创建：",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "sldataapi apikey create <id> admin",
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        "Key 的权限在创建时就定了。要值班（duty）Key，创建时把最后的 admin 写成 duty。之后要 admin 权限，就在游戏里再建一把 admin Key，到应用里换上。Key 可以先不填，只看监控。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = host,
        onValueChange = onHost,
        label = { Text("主机（IP 或域名）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    OutlinedTextField(
        value = port,
        onValueChange = onPort,
        label = { Text("端口") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    OutlinedTextField(
        value = verifyToken,
        onValueChange = onVerify,
        label = { Text("VerifyToken") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKey,
        label = { Text("API Key（可选）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    OutlinedButton(onClick = onTest, enabled = !testing) {
        Text(if (testing) "测试中…" else "测试连接")
    }
    testResult?.let { (ok, message) ->
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
    }
    formError?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TourStep() {
    Text("主要功能", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "底栏有五项。中心里还能进适配插件和设置。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TOUR.forEach { card ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    card.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        card.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentStep(selected: Boolean?, onPick: (Boolean) -> Unit) {
    Text("使用统计", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "匿名使用统计（Microsoft Clarity）用来看哪些界面不好用，好继续改这个应用。服务器地址、密钥、控制台内容和玩家昵称会被遮住，不会原样上传。",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "跳过的话保持现在的默认：开启。你可以以后在设置里关掉。" +
            "除此之外，应用只在检查更新时访问 GitHub；服务器数据只在手机和你自己的服务器之间传输。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected == true) {
            Button(onClick = { onPick(true) }, modifier = Modifier.weight(1f)) { Text("开启") }
        } else {
            OutlinedButton(onClick = { onPick(true) }, modifier = Modifier.weight(1f)) { Text("开启") }
        }
        if (selected == false) {
            Button(onClick = { onPick(false) }, modifier = Modifier.weight(1f)) { Text("不开启") }
        } else {
            OutlinedButton(onClick = { onPick(false) }, modifier = Modifier.weight(1f)) { Text("不开启") }
        }
    }
}

@Composable
private fun DoneStep() {
    Text("可以开始了", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "用底栏在概览、玩家、控制台、地图和中心之间切换。以后想再看一遍，到设置或关于里点「重新查看新手引导」。",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun StepDots(step: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEP_COUNT) { index ->
            val active = index == step
            Spacer(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${step + 1} / $STEP_COUNT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private suspend fun saveQuickServer(
    store: com.dntof.slconsole.data.local.ServerStore,
    draftId: String?,
    host: String,
    port: String,
    verifyToken: String,
    apiKey: String,
    onDraftId: (String) -> Unit,
): String? {
    val hostText = host.trim()
    val tokenBlank = verifyToken.isBlank()
    val keyBlank = apiKey.isBlank()
    if (hostText.isEmpty() && tokenBlank && keyBlank) return null
    val p = port.toIntOrNull()
    return when {
        hostText.isEmpty() || tokenBlank -> "主机和 VerifyToken 要一起填，或者这一步留空"
        p == null || p !in 1..65535 -> "端口必须是 1-65535"
        else -> {
            val id = draftId ?: ServerConfig.newId()
            onDraftId(id)
            val config = ServerConfig(
                id = id,
                host = hostText,
                port = p,
                verifyToken = verifyToken.filter { it.code in 32..126 }.trim(),
                apiKey = apiKey.filter { it.code in 32..126 }.trim(),
                createdAt = System.currentTimeMillis(),
            )
            try {
                store.upsert(config)
                ControlRepository.closeServer(config.id)
                store.setActive(config.id)
                null
            } catch (e: Exception) {
                "保存失败：${e.message ?: "凭据加密异常"}"
            }
        }
    }
}
