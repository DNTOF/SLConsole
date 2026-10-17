// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.remote.tls.CertFingerprint
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

    /** 加密握手成功后写上闩。已经是 true 时什么都不改。 */
    suspend fun markTlsSeen(id: String) {
        context.serverDataStore.edit { prefs ->
            val list = prefs[Keys.SERVERS]?.let { decodeServers(it) } ?: return@edit
            if (list.none { it.id == id && !it.tlsSeen }) return@edit
            val updated = list.map { if (it.id == id) it.copy(tlsSeen = true) else it }
            prefs[Keys.SERVERS] = encodeServers(updated)
        }
    }

    /** 改指纹或加密锁。指纹请先规范化;空字符串表示还没固定。 */
    suspend fun updateSecurity(id: String, fingerprint: String, tlsSeen: Boolean) {
        context.serverDataStore.edit { prefs ->
            val list = prefs[Keys.SERVERS]?.let { decodeServers(it) } ?: return@edit
            if (list.none { it.id == id }) return@edit
            val updated = list.map {
                if (it.id == id) it.copy(certFingerprint = fingerprint, tlsSeen = tlsSeen) else it
            }
            prefs[Keys.SERVERS] = encodeServers(updated)
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
                certFingerprint = CertFingerprint.normalize(decrypted.certFingerprint).orEmpty(),
            )
        }
    }
}
