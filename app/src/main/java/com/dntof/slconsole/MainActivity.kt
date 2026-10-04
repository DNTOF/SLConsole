// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.dntof.slconsole.data.local.GlassGuard
import com.dntof.slconsole.security.AppLock
import com.dntof.slconsole.ui.AppRoot
import com.dntof.slconsole.ui.theme.SLConsoleTheme
import com.dntof.slconsole.ui.visibleKeyboardOverlapPx

/**
 * BiometricPrompt 需要 FragmentActivity。FragmentActivity 本身继承自 androidx.activity.ComponentActivity，
 * enableEdgeToEdge、setContent 和下面的键盘 inset 处理都不受影响。
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // enableEdgeToEdge 会关掉 decorFitsSystemWindows。HyperOS 这时经常改走 adjustPan,
        // 整页 Compose 视图又被认为已经可见,于是既不缩小窗口也不下发 IME inset。
        ensureAdjustResize()
        if (Build.VERSION.SDK_INT >= 29) {
            // 关闭系统强制的导航栏对比度遮罩,否则底栏下方会剩一条不透明色带
            window.isNavigationBarContrastEnforced = false
            // API 35 起该开关已废弃,系统不再靠它给状态栏垫色
            if (Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
            }
        }
        val startupNotice = GlassGuard.recoverIfNeeded(this)
        setContent {
            SLConsoleTheme {
                AppRoot(startupNotice = startupNotice)
            }
        }
        installImeInsetFallback()
    }

    override fun onStart() {
        super.onStart()
        AppLock.onAppStarted()
    }

    override fun onResume() {
        super.onResume()
        ensureAdjustResize()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onAppStopped()
    }

    @Suppress("DEPRECATION")
    private fun ensureAdjustResize() {
        // API 30 起改由 WindowInsets 表达键盘,但 HyperOS 仍要这个标志才会下发 IME inset。
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    /**
     * 系统把 IME inset 报成 0、但可见区域确实被键盘挡住时,补上 inset 再交给 Compose。
     * 已有非 0 的 IME inset 时原样返回,避免和内容 padding 叠成两倍空白。
     */
    private fun installImeInsetFallback() {
        val content = findViewById<android.view.View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (imeBottom > 0) return@setOnApplyWindowInsetsListener insets
            val overlap = visibleKeyboardOverlapPx(view)
            if (overlap <= 0) return@setOnApplyWindowInsetsListener insets
            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.ime(),
                    androidx.core.graphics.Insets.of(0, 0, 0, overlap),
                )
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
        }
    }
}
