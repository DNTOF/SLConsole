// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.update

import android.os.Build
import com.dntof.slconsole.BuildConfig
import com.dntof.slconsole.data.local.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 检查更新。自动检查最多每 [UpdateConfig.AUTO_CHECK_INTERVAL_MS] 一次，出错不打扰用户；
 * 手动检查忽略间隔和「忽略此版本」，结果交给调用方提示。
 */
object UpdateChecker {
    sealed interface Outcome {
        data class Available(val info: UpdateInfo) : Outcome
        data class UpToDate(val latest: String?) : Outcome
        data class Incompatible(val info: UpdateInfo) : Outcome
        data class Unavailable(val reason: String) : Outcome
        data class Failed(val message: String) : Outcome
        data object Skipped : Outcome
    }

    private val _pending = MutableStateFlow<UpdateInfo?>(null)

    /** 需要弹窗的新版本。弹窗关闭后清空。 */
    val pending: StateFlow<UpdateInfo?> = _pending.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    var source: UpdateSource = UpdateConfig.createSource()

    fun dismiss() {
        _pending.value = null
    }

    suspend fun ignore(store: SettingsStore, info: UpdateInfo) {
        store.setIgnoredUpdateVersion(info.versionName)
        _pending.value = null
    }

    @Volatile
    private var autoCheckedThisProcess = false

    /** 冷启动时调用：每个进程一次，开关打开且距离上次检查超过间隔才会联网。 */
    suspend fun autoCheck(store: SettingsStore, now: Long = System.currentTimeMillis()): Outcome {
        if (autoCheckedThisProcess) return Outcome.Skipped
        autoCheckedThisProcess = true
        if (!store.autoUpdateCheckFlow.first()) return Outcome.Skipped
        val last = store.lastUpdateCheckFlow.first()
        if (last > 0 && now - last in 0 until UpdateConfig.AUTO_CHECK_INTERVAL_MS) return Outcome.Skipped
        return check(store, manual = false)
    }

    suspend fun check(store: SettingsStore, manual: Boolean): Outcome {
        if (!mutex.tryLock()) return Outcome.Skipped
        _checking.value = true
        try {
            val result = source.fetchLatest()
            // 只有真正联系上更新源才记时间，断网时下次启动还会再试。
            if (result !is FetchResult.Failed) store.setLastUpdateCheck(System.currentTimeMillis())
            return when (result) {
                is FetchResult.Failed -> Outcome.Failed(result.message)
                is FetchResult.Unavailable -> Outcome.Unavailable(result.reason)
                is FetchResult.Found -> {
                    val info = result.info
                    when {
                        !AppVersion.isNewer(info, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE) ->
                            Outcome.UpToDate(info.versionName)
                        info.minSdk != null && info.minSdk > Build.VERSION.SDK_INT -> Outcome.Incompatible(info)
                        !manual && store.ignoredUpdateVersionFlow.first() == info.versionName ->
                            Outcome.Skipped
                        else -> {
                            _pending.value = info
                            Outcome.Available(info)
                        }
                    }
                }
            }
        } finally {
            _checking.value = false
            mutex.unlock()
        }
    }

    /** 手动检查结果的提示文案；有新版本时弹窗，不需要文案。 */
    fun describe(outcome: Outcome): String? = when (outcome) {
        is Outcome.Available -> null
        is Outcome.UpToDate -> "已是最新版本（${BuildConfig.VERSION_NAME}）"
        is Outcome.Incompatible -> "发现 ${outcome.info.versionName}，但它需要 Android API ${outcome.info.minSdk} 以上"
        is Outcome.Unavailable -> "${outcome.reason}，暂时无法检查更新"
        is Outcome.Failed -> "检查失败：${outcome.message}"
        Outcome.Skipped -> "正在检查，请稍候"
    }
}
