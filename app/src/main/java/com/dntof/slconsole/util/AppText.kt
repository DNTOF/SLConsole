// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * 给 Composable 以外的代码(数据层、回调、协程)取界面文字。
 *
 * API 33 起系统会把应用内语言应用到整个进程,直接用 Application 的资源即可;
 * 更早的版本 AppCompat 只改写 Activity 的 Configuration,这里按
 * [AppCompatDelegate.getApplicationLocales] 另建一份带语言的 Context。
 */
object AppText {
    // 只保存 applicationContext,不会泄漏 Activity
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var app: Context? = null

    @Volatile
    private var cached: Pair<String, Resources>? = null

    /** 纯 JVM 单元测试没有 Android 资源,由测试注入。 */
    @Volatile
    var testResolver: ((id: Int, quantity: Int?, args: Array<out Any?>) -> String)? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun resources(): Resources {
        val ctx = checkNotNull(app) { "AppText.init() has not been called" }
        if (Build.VERSION.SDK_INT >= 33) return ctx.resources
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ctx.resources
        val tags = locales.toLanguageTags()
        cached?.let { (key, res) -> if (key == tags) return res }
        val config = Configuration(ctx.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tags))
        val res = ctx.createConfigurationContext(config).resources
        cached = tags to res
        return res
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        testResolver?.let { return it(id, null, args) }
        val res = resources()
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String {
        testResolver?.let { return it(id, quantity, args) }
        return resources().getQuantityString(id, quantity, *args)
    }
}

fun appString(@StringRes id: Int, vararg args: Any?): String = AppText.get(id, *args)

fun appPlural(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String = AppText.plural(id, quantity, *args)
