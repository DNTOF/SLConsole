// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 生物识别解锁。开关存在 SettingsStore 里，这里只管「这次进程里有没有解过锁」和弹认证框。
 *
 * API 30 起可以直接用 BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL。
 * API 26-29 上 STRONG 不能和 DEVICE_CREDENTIAL 组合，改用 BIOMETRIC_WEAK | DEVICE_CREDENTIAL
 * （1.1.0 在旧系统上会退回 KeyguardManager 的锁屏密码界面）。
 * 如果这种组合在某些机型上报不可用、但单独的生物识别可用，就只用生物识别，并给出「取消」按钮。
 */
object AppLock {
    private const val TAG = "AppLock"

    /** 回到前台时，离开超过这么久才重新上锁。 */
    const val RELOCK_AFTER_MS = 30_000L

    enum class Availability { Ready, NoneEnrolled, NoHardware, Unavailable }

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data object Unavailable : Result
        data class Error(val message: String) : Result
    }

    private data class Mode(val authenticators: Int, val needsNegativeButton: Boolean)

    private val _unlocked = MutableStateFlow(false)

    /** 冷启动时为 false。开关打开时，界面在它变成 true 之前不显示任何内容。 */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** 认证框（或系统锁屏密码界面）正在显示。这期间 Activity 进后台不算离开。 */
    @Volatile
    var authenticating: Boolean = false
        private set

    private var stoppedAt = 0L

    fun markUnlocked() {
        _unlocked.value = true
    }

    /** MainActivity.onStop 调用。 */
    fun onAppStopped() {
        if (authenticating) return
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /** MainActivity.onStart 调用。离开超过 30 秒就重新上锁，是否真的显示锁屏由开关决定。 */
    fun onAppStarted() {
        val since = stoppedAt
        stoppedAt = 0L
        if (since == 0L || authenticating) return
        if (SystemClock.elapsedRealtime() - since > RELOCK_AFTER_MS) {
            _unlocked.value = false
            Log.i(TAG, "relock after background")
        }
    }

    private fun primaryAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    private fun mode(context: Context): Mode? {
        val manager = BiometricManager.from(context)
        val primary = primaryAuthenticators()
        if (manager.canAuthenticate(primary) == BiometricManager.BIOMETRIC_SUCCESS) {
            return Mode(primary, needsNegativeButton = false)
        }
        if (manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            return Mode(BIOMETRIC_WEAK, needsNegativeButton = true)
        }
        return null
    }

    fun availability(context: Context): Availability {
        if (mode(context) != null) return Availability.Ready
        return when (BiometricManager.from(context).canAuthenticate(primaryAuthenticators())) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.NoHardware
            else -> Availability.Unavailable
        }
    }

    fun unavailableText(availability: Availability): String = when (availability) {
        Availability.Ready -> ""
        Availability.NoneEnrolled -> "这台设备还没有录入指纹或面容，也没有设置锁屏密码。先在系统设置里设置一个，再回来打开。"
        Availability.NoHardware -> "这台设备不支持生物识别。"
        Availability.Unavailable -> "这台设备现在不能使用生物识别或锁屏密码。"
    }

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String = "用指纹、面容或锁屏密码确认是你本人",
        onResult: (Result) -> Unit,
    ) {
        val activity = context.findFragmentActivity()
        val mode = mode(context)
        if (activity == null || mode == null) {
            onResult(Result.Unavailable)
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false
                Log.i(TAG, "auth succeeded")
                onResult(Result.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticating = false
                Log.i(TAG, "auth error $errorCode")
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onResult(if (cancelled) Result.Cancelled else Result.Error(errString.toString()))
            }

            override fun onAuthenticationFailed() {
                // 指纹没认出来。系统框还开着，可以再试，不用处理。
                Log.i(TAG, "auth failed attempt")
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(mode.authenticators)
            .setConfirmationRequired(false)
            .apply { if (mode.needsNegativeButton) setNegativeButtonText("取消") }
            .build()
        try {
            authenticating = true
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
        } catch (t: Throwable) {
            authenticating = false
            Log.w(TAG, "auth start failed", t)
            onResult(Result.Error(t.message ?: "无法打开认证"))
        }
    }

    private fun Context.findFragmentActivity(): FragmentActivity? {
        var current: Context? = this
        while (current != null) {
            if (current is FragmentActivity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }
}
