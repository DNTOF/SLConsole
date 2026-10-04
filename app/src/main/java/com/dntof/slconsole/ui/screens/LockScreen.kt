// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.security.AppLock
import com.dntof.slconsole.ui.components.AppIconBadge
import com.microsoft.clarity.modifiers.clarityMask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 打开了生物识别解锁、且这次还没解锁时，AppRoot 只画这一屏，主界面完全不组合。
 * 出现后等 Activity 进入 RESUMED 自动弹一次认证框；取消或失败留在这里，点「解锁」再试。
 */
@Composable
fun AppLockGate() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun prompt() {
        if (busy || AppLock.authenticating) return
        val availability = AppLock.availability(context)
        if (availability == AppLock.Availability.NoneEnrolled) {
            // 设备上已经没有任何指纹、面容和锁屏密码，这把锁没法再解开，也没有保护作用。
            // 关掉它，免得把自己锁在外面。
            scope.launch { ServiceLocator.settingsStore.setBiometricLock(false) }
            AppLock.markUnlocked()
            return
        }
        if (availability != AppLock.Availability.Ready) {
            message = AppLock.unavailableText(availability)
            return
        }
        busy = true
        message = null
        AppLock.authenticate(context, title = "解锁 SLConsole") { result ->
            busy = false
            when (result) {
                AppLock.Result.Success -> AppLock.markUnlocked()
                AppLock.Result.Cancelled -> message = "已取消。点「解锁」再试一次。"
                AppLock.Result.Unavailable -> message = "现在不能使用生物识别或锁屏密码。"
                is AppLock.Result.Error -> message = result.message
            }
        }
    }

    LaunchedEffect(Unit) {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        prompt()
    }

    LockScreen(message = message, busy = busy, onUnlock = { prompt() })
}

@Composable
private fun LockScreen(message: String?, busy: Boolean, onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize().clarityMask(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIconBadge(size = 88.dp)
            Spacer(Modifier.height(20.dp))
            Text("SLConsole 已锁定", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "用指纹、面容或锁屏密码解锁后才能查看。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, enabled = !busy) { Text("解锁") }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
