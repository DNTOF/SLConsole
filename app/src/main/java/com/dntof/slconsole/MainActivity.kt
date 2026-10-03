package com.dntof.slconsole

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dntof.slconsole.data.local.GlassGuard
import com.dntof.slconsole.ui.AppRoot
import com.dntof.slconsole.ui.theme.SLConsoleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
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
    }
}
