package com.dntof.slconsole.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.dntof.slconsole.analytics.ClarityDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "slconsole_settings")

/** 与服务器凭据分开存放的本机外观偏好。液态玻璃默认关闭。 */
class SettingsStore(private val context: Context) {

    private val liquidGlassKey = booleanPreferencesKey("liquid_glass")
    private val usageAnalyticsKey = booleanPreferencesKey("usage_analytics")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock")

    val liquidGlassFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[liquidGlassKey] ?: false
    }

    val usageAnalyticsFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[usageAnalyticsKey] ?: ClarityDefaults.ENABLED
    }

    /** 没有写过这个键就是第一次打开。引导走完或点「跳过」之后才是 true。 */
    val onboardingCompletedFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[onboardingCompletedKey] ?: false
    }

    suspend fun setLiquidGlass(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[liquidGlassKey] = enabled
        }
    }

    suspend fun setUsageAnalytics(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[usageAnalyticsKey] = enabled
        }
    }

    /** 生物识别解锁，默认关闭。只有认证成功一次之后才会写成 true。 */
    val biometricLockFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[biometricLockKey] ?: false
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[biometricLockKey] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[onboardingCompletedKey] = completed
        }
    }
}
