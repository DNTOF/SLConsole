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
import com.dntof.slconsole.data.model.HostAddress
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.SlHttpClient
import com.dntof.slconsole.data.remote.tls.CertFingerprint
import com.dntof.slconsole.data.remote.tls.TransportFailure
import com.dntof.slconsole.data.remote.tls.TransportStatus
import com.dntof.slconsole.ui.components.MismatchDialog
import com.dntof.slconsole.ui.components.TofuDialog
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.ui.LocalImeLift
import com.dntof.slconsole.security.AppLock
import com.dntof.slconsole.ui.components.AppIconBadge
import com.dntof.slconsole.ui.components.SecretOutlinedField
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.util.stripRichText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString
import androidx.annotation.StringRes

private const val STEP_COUNT = 6

/** 使用统计是第 4 步（从 0 数是 3）。在这之前跳过，统计保持关闭。 */
private const val ANALYTICS_STEP = 3

private data class TourCard(@StringRes val title: Int, @StringRes val body: Int, val icon: ImageVector)

private val TOUR = listOf(
    TourCard(
        R.string.common_overview,
        R.string.onboarding_first_tab_see_whether_server,
        Icons.Outlined.SpaceDashboard,
    ),
    TourCard(
        R.string.common_players,
        R.string.onboarding_second_tab_search_online_players,
        Icons.Outlined.Groups,
    ),
    TourCard(
        R.string.common_console,
        R.string.onboarding_third_tab_send_commands_straight,
        Icons.Outlined.Terminal,
    ),
    TourCard(
        R.string.common_map,
        R.string.onboarding_fourth_tab_draws_facility_your,
        Icons.Outlined.Map,
    ),
    TourCard(
        R.string.common_hub,
        R.string.onboarding_last_tab_adapted_plugins_files,
        Icons.Outlined.Apps,
    ),
)

/**
 * 第一次打开时盖在主界面上。用普通纯色表面，不跟液态玻璃开关走。
 * 在走到使用统计那一步之前点「跳过」，会把使用统计写成关闭。走到那一步之后再跳过，不改已经保存的选择。
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
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var trustPrompt by remember { mutableStateOf<TransportStatus.Prompt?>(null) }
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
        val turnAnalyticsOff = !replay && analyticsChoice == null && step < ANALYTICS_STEP
        scope.launch {
            if (turnAnalyticsOff) settings.setUsageAnalytics(false)
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
                fingerprint = fingerprint,
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
                        stringResource(R.string.common_onboarding),
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
                            fingerprint = fingerprint,
                            testing = testing,
                            testResult = testResult,
                            formError = formError,
                            onHost = { host = it },
                            onPort = { port = it },
                            onVerify = { verifyToken = it },
                            onApiKey = { apiKey = it },
                            onFingerprint = { fingerprint = it },
                            onTest = {
                                val h = host.trim()
                                val p = port.toIntOrNull() ?: ServerConfig.DEFAULT_PORT
                                val hostError = HostAddress.problem(h)
                                if (hostError != null || verifyToken.isBlank()) {
                                    testResult = false to (hostError ?: appString(R.string.common_enter_host_address_verifytoken_first))
                                    return@AddServerStep
                                }
                                val pin = if (fingerprint.isBlank()) "" else CertFingerprint.normalize(fingerprint)
                                if (fingerprint.isNotBlank() && pin == null) {
                                    testResult = false to appString(R.string.common_fingerprint_must_32_byte_sha)
                                    return@AddServerStep
                                }
                                testing = true
                                testResult = null
                                scope.launch {
                                    val temp = ServerConfig(
                                        id = "test",
                                        host = h,
                                        port = p,
                                        verifyToken = verifyToken,
                                        certFingerprint = pin.orEmpty(),
                                    )
                                    testResult = when (val result = SlHttpClient().getData(temp, "/get_sl_data", useCache = false)) {
                                        is SlHttpClient.HttpResult.Success -> {
                                            val name = (result.body["server_name"] as? JsonPrimitive)?.content?.let { stripRichText(it) }
                                            val count = (result.body["players_count"] as? JsonPrimitive)?.content
                                            val lock = if (result.encrypted) appString(R.string.common_encrypted) else appString(R.string.common_plaintext)
                                            true to appString(R.string.onboarding_connected_online, lock, name ?: appString(R.string.onboarding_unnamed_server), count ?: "?")
                                        }
                                        is SlHttpClient.HttpResult.Failure -> {
                                            trustPrompt = when (val tls = result.tls) {
                                                is TransportFailure.Tofu -> TransportStatus.Prompt.Tofu(tls.fingerprint)
                                                is TransportFailure.Mismatch -> TransportStatus.Prompt.Mismatch(tls.pinned, tls.presented)
                                                else -> null
                                            }
                                            false to result.message
                                        }
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
                                AppLock.authenticate(context, title = appString(R.string.common_turn_biometric_unlock)) { result ->
                                    when (result) {
                                        AppLock.Result.Success -> {
                                            AppLock.markUnlocked()
                                            lockChoice = true
                                            scope.launch { settings.setBiometricLock(true) }
                                        }
                                        AppLock.Result.Cancelled -> lockMessage = appString(R.string.common_cancelled_not_turned)
                                        AppLock.Result.Unavailable -> lockMessage = appString(R.string.common_biometrics_screen_lock_cant_used)
                                        is AppLock.Result.Error -> lockMessage = appString(R.string.common_not_turned, result.message)
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
                        TextButton(onClick = { finish() }, enabled = !finishing) { Text(stringResource(R.string.onboarding_skip)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { if (step > 0) step -= 1 }, enabled = step > 0 && !finishing) {
                            Text(stringResource(R.string.onboarding_back))
                        }
                        Button(onClick = { goNext() }, enabled = !finishing && !testing && !saving) {
                            Text(if (step >= STEP_COUNT - 1) stringResource(R.string.onboarding_done) else stringResource(R.string.onboarding_next))
                        }
                    }
                }
            }
        }
    }
    when (val prompt = trustPrompt) {
        is TransportStatus.Prompt.Tofu -> TofuDialog(
            fingerprint = prompt.fingerprint,
            onTrust = {
                CertFingerprint.normalize(prompt.fingerprint)?.let { fingerprint = it }
                trustPrompt = null
            },
            onDismiss = { trustPrompt = null },
        )
        is TransportStatus.Prompt.Mismatch -> MismatchDialog(
            pinned = prompt.pinned,
            presented = prompt.presented,
            onTrust = {
                CertFingerprint.normalize(prompt.presented)?.let { fingerprint = it }
                trustPrompt = null
            },
            onDismiss = { trustPrompt = null },
        )
        null -> Unit
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
        stringResource(R.string.onboarding_welcome_slconsole),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.staggerIn(progress, 1),
    )
    Text(
        stringResource(R.string.onboarding_mobile_client_viewing_managing_scp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.staggerIn(progress, 2),
    )
    FlowRow(
        modifier = Modifier.staggerIn(progress, 3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(stringResource(R.string.onboarding_live_monitoring), stringResource(R.string.onboarding_remote_control), stringResource(R.string.onboarding_map_voice), stringResource(R.string.onboarding_files_plugins)).forEach { label ->
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
    Text(stringResource(R.string.common_biometric_unlock), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.onboarding_when_need_unlock_fingerprint_face),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (availability != AppLock.Availability.Ready) {
        Text(
            stringResource(R.string.onboarding_unavailable, AppLock.unavailableText(availability)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected == true) {
            Button(onClick = onEnable, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_turn)) }
        } else {
            OutlinedButton(onClick = onEnable, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_turn)) }
        }
        if (selected == false) {
            Button(onClick = onDecline, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_not_now)) }
        } else {
            OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_not_now)) }
        }
    }
    when {
        message != null -> Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        selected == true -> Text(stringResource(R.string.onboarding_turned), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        selected == false -> Text(stringResource(R.string.onboarding_not_turned_now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddServerStep(
    host: String,
    port: String,
    verifyToken: String,
    apiKey: String,
    fingerprint: String,
    testing: Boolean,
    testResult: Pair<Boolean, String>?,
    formError: String?,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onVerify: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onFingerprint: (String) -> Unit,
    onTest: () -> Unit,
) {
    Text(stringResource(R.string.onboarding_add_server), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.onboarding_can_leave_step_empty_if),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.onboarding_verify_token_reads_monitoring_data),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "sldataapi apikey create <id> admin",
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        stringResource(R.string.onboarding_keys_permissions_are_fixed_when),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val hostError = if (host.isBlank()) null else HostAddress.problem(host.trim())
    OutlinedTextField(
        value = host,
        onValueChange = onHost,
        label = { Text(stringResource(R.string.onboarding_host_ip_domain)) },
        isError = hostError != null,
        supportingText = hostError?.let { message -> { Text(message) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    OutlinedTextField(
        value = port,
        onValueChange = onPort,
        label = { Text(stringResource(R.string.onboarding_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    SecretOutlinedField(
        value = verifyToken,
        onValueChange = onVerify,
        label = { Text("VerifyToken") },
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    SecretOutlinedField(
        value = apiKey,
        onValueChange = onApiKey,
        label = { Text(stringResource(R.string.onboarding_api_key_optional)) },
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
    )
    OutlinedTextField(
        value = fingerprint,
        onValueChange = onFingerprint,
        label = { Text(stringResource(R.string.onboarding_certificate_fingerprint_optional)) },
        supportingText = { Text(stringResource(R.string.onboarding_can_paste_advance_if_left)) },
        modifier = Modifier.fillMaxWidth().keepAboveIme(),
        minLines = 2,
    )
    OutlinedButton(onClick = onTest, enabled = !testing) {
        Text(if (testing) stringResource(R.string.common_testing) else stringResource(R.string.common_test_connection))
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
    Text(stringResource(R.string.onboarding_main_features), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.onboarding_bottom_bar_has_five_tabs),
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
                    Text(stringResource(card.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(card.body),
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
    Text(stringResource(R.string.common_usage_statistics), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.onboarding_anonymous_usage_statistics_microsoft_clarity),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        stringResource(R.string.onboarding_if_tap_skip_before_reaching),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected == true) {
            Button(onClick = { onPick(true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_turn)) }
        } else {
            OutlinedButton(onClick = { onPick(true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_turn)) }
        }
        if (selected == false) {
            Button(onClick = { onPick(false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_dont_turn)) }
        } else {
            OutlinedButton(onClick = { onPick(false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_dont_turn)) }
        }
    }
}

@Composable
private fun DoneStep() {
    Text(stringResource(R.string.onboarding_youre_all_set), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.onboarding_use_bottom_bar_switch_between),
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
    fingerprint: String,
    onDraftId: (String) -> Unit,
): String? {
    val hostText = host.trim()
    val tokenBlank = verifyToken.isBlank()
    val keyBlank = apiKey.isBlank()
    if (hostText.isEmpty() && tokenBlank && keyBlank) return null
    if (hostText.isNotEmpty()) {
        HostAddress.problem(hostText)?.let { return it }
    }
    val p = port.toIntOrNull()
    val pin = if (fingerprint.isBlank()) "" else CertFingerprint.normalize(fingerprint)
    if (fingerprint.isNotBlank() && pin == null) return appString(R.string.common_fingerprint_must_32_byte_sha)
    return when {
        hostText.isEmpty() || tokenBlank -> appString(R.string.onboarding_fill_both_host_verifytoken_leave)
        p == null || p !in 1..65535 -> appString(R.string.onboarding_port_must_1_65535)
        else -> {
            val id = draftId ?: ServerConfig.newId()
            onDraftId(id)
            val config = ServerConfig(
                id = id,
                host = hostText,
                port = p,
                verifyToken = verifyToken.filter { it.code in 32..126 }.trim(),
                apiKey = apiKey.filter { it.code in 32..126 }.trim(),
                certFingerprint = pin.orEmpty(),
                createdAt = System.currentTimeMillis(),
            )
            try {
                store.upsert(config)
                ControlRepository.closeServer(config.id)
                store.setActive(config.id)
                null
            } catch (e: Exception) {
                appString(R.string.onboarding_save_failed, e.message ?: appString(R.string.onboarding_credential_encryption_error))
            }
        }
    }
}
