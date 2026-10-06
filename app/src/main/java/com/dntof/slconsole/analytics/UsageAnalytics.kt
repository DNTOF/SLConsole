// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.analytics

import android.content.Context
import android.util.Log
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig
import com.microsoft.clarity.models.LogLevel

/**
 * 匿名使用统计的默认开关。想改默认值时只改 [ENABLED]。
 * 关闭时不会调用 Clarity.initialize，也不会上传。
 */
object ClarityDefaults {
    const val ENABLED = true
    const val PROJECT_ID = "yruzrxozxh"
}

/**
 * 只有开关为开时才初始化。第一次打开时，界面会等新手引导结束或被跳过之后才调用这里。
 * 选「不开启」，或在走到使用统计那一步之前跳过，偏好会写成关闭，Clarity 不会被创建。
 * 网络不可用时 SDK 会先把数据留在本机；这里再包一层，避免初始化异常把应用打崩。
 */
object UsageAnalytics {
    private const val TAG = "UsageAnalytics"
    private var initialized = false

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) enable(context) else disable()
    }

    private fun enable(context: Context) {
        try {
            if (!initialized) {
                val config = ClarityConfig(ClarityDefaults.PROJECT_ID)
                config.logLevel = LogLevel.None
                // clarity-compose 3.10.0 的公开 ClarityConfig 已经没有 enableWebViewCapture。
                // WebView 采集改由项目下发的 DynamicConfig.disableWebViewCapture 控制，客户端设不了。
                // 这个应用本身也不嵌入 WebView。
                val ok = Clarity.initialize(context.applicationContext, config)
                if (ok != true) {
                    Log.i(TAG, "clarity initialize returned false")
                    return
                }
                initialized = true
                Log.i(TAG, "clarity initialize")
            }
            if (Clarity.isPaused() == true) {
                Clarity.resume()
                Log.i(TAG, "clarity resume")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "clarity initialize failed", t)
        }
    }

    private fun disable() {
        if (!initialized) {
            Log.i(TAG, "clarity skipped")
            return
        }
        try {
            if (Clarity.isPaused() != true) {
                Clarity.pause()
                Log.i(TAG, "clarity pause")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "clarity pause failed", t)
        }
    }
}
