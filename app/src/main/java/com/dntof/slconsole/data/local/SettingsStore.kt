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

    val liquidGlassFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[liquidGlassKey] ?: false
    }

    val usageAnalyticsFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[usageAnalyticsKey] ?: ClarityDefaults.ENABLED
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
}
