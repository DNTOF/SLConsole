// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.local

import android.content.Context
import com.dntof.slconsole.ServiceLocator
import kotlinx.coroutines.runBlocking
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

/**
 * 玻璃绘制一旦崩溃,DataStore 里的开关还在,下次启动会立刻再崩。
 * 用同步的 SharedPreferences 记一笔「已开始绘制、尚未确认成功」。
 * 进程死在绘制过程中时,下次启动会清掉开关。
 */
object GlassGuard {
    private const val PREFS = "slconsole_glass_guard"
    private const val PENDING = "render_pending"
    val RECOVERY_NOTICE: String get() = appString(R.string.glass_guard_liquid_glass_failed_render_last)
    val FAILURE_NOTICE: String get() = appString(R.string.glass_guard_liquid_glass_failed_render_was)

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
