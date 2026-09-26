package com.dntof.slconsole.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.serverDataStore by preferencesDataStore(name = "slconsole_servers")

/**
 * 服务器配置仓库:DataStore 持久化,VerifyToken / API Key 经 CryptoBox 加密后落盘。
 */
class ServerStore(private val context: Context) {

    private object Keys {
        val SERVERS = stringPreferencesKey("servers_json")
        val ACTIVE = stringPreferencesKey("active_id")
    }

    val serversFlow: Flow<List<ServerConfig>> = context.serverDataStore.data.map { prefs ->
        prefs[Keys.SERVERS]?.let { decodeServers(it) } ?: emptyList()
    }

    val activeIdFlow: Flow<String?> = context.serverDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE]
    }

    suspend fun upsert(config: ServerConfig) {
        context.serverDataStore.edit { prefs ->
            val list = prefs[Keys.SERVERS]?.let { decodeServers(it) } ?: emptyList()
            val updated = list.filterNot { it.id == config.id } + config
            prefs[Keys.SERVERS] = encodeServers(updated.sortedBy { it.createdAt })
        }
    }

    suspend fun delete(id: String) {
        context.serverDataStore.edit { prefs ->
            val list = prefs[Keys.SERVERS]?.let { decodeServers(it) } ?: emptyList()
            val remaining = list.filterNot { it.id == id }
            prefs[Keys.SERVERS] = encodeServers(remaining)
            if (prefs[Keys.ACTIVE] == id) {
                val nextActive = remaining.firstOrNull()?.id
                if (nextActive == null) prefs.remove(Keys.ACTIVE) else prefs[Keys.ACTIVE] = nextActive
            }
        }
    }

    suspend fun setActive(id: String?) {
        context.serverDataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.ACTIVE) else prefs[Keys.ACTIVE] = id
        }
    }

    private fun encodeServers(list: List<ServerConfig>): String {
        val stored = list.map {
            it.copy(
                verifyToken = CryptoBox.encrypt(it.verifyToken),
                apiKey = CryptoBox.encrypt(it.apiKey),
            )
        }
        return AppJson.json.encodeToString(stored)
    }

    private fun decodeServers(raw: String): List<ServerConfig> {
        val stored = runCatching {
            AppJson.json.decodeFromString<List<ServerConfig>>(raw)
        }.getOrDefault(emptyList())
        return stored.map {
            val decrypted = it.copy(
                verifyToken = CryptoBox.decrypt(it.verifyToken),
                apiKey = CryptoBox.decrypt(it.apiKey),
            )
            // 加载时清洗:历史数据里可能残留剪贴板带入的控制字符
            decrypted.copy(
                verifyToken = decrypted.verifyToken.filter { c -> c.code in 32..126 }.trim(),
                apiKey = decrypted.apiKey.filter { c -> c.code in 32..126 }.trim(),
            )
        }
    }
}
