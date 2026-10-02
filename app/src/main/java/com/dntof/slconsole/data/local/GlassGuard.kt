package com.dntof.slconsole.data.local

import android.content.Context
import com.dntof.slconsole.ServiceLocator
import kotlinx.coroutines.runBlocking

/**
 * 玻璃绘制一旦崩溃,DataStore 里的开关还在,下次启动会立刻再崩。
 * 用同步的 SharedPreferences 记一笔「已开始绘制、尚未确认成功」。
 * 进程死在绘制过程中时,下次启动会清掉开关。
 */
object GlassGuard {
    private const val PREFS = "slconsole_glass_guard"
    private const val PENDING = "render_pending"
    const val RECOVERY_NOTICE = "液态玻璃上次绘制失败,已自动关闭。"
    const val FAILURE_NOTICE = "液态玻璃绘制失败,已自动关闭。"

    fun recoverIfNeeded(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PENDING, false)) return null
        prefs.edit().putBoolean(PENDING, false).commit()
        runBlocking { ServiceLocator.settingsStore.setLiquidGlass(false) }
        return RECOVERY_NOTICE
    }

    fun markRenderStart(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PENDING, true)
            .commit()
    }

    fun markRenderOk(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PENDING, false)
            .commit()
    }
}
