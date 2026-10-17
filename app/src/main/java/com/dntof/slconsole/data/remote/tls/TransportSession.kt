// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient

/**
 * 轮询期间记住这一台服务器这次是加密还是明文,避免每 5 秒都先撞一次失败的 TLS。
 * 刷新、改配置、加密成功都会清掉明文记录,下一次仍然先试 TLS。
 */
object TransportSession {
    enum class Channel { HTTP, VOICE }

    data class Choice(val secure: Boolean, val client: OkHttpClient)

    private val choices = ConcurrentHashMap<String, Choice>()

    fun key(channel: Channel, serverId: String, host: String, port: Int, pin: String?): String {
        val canonical = CertFingerprint.normalize(pin).orEmpty()
        return "$serverId|$channel|$host|$port|$canonical"
    }

    fun get(key: String, tlsSeen: Boolean): Choice? {
        val choice = choices[key] ?: return null
        if (!choice.secure && tlsSeen) {
            choices.remove(key)
            return null
        }
        return choice
    }

    fun put(key: String, choice: Choice) {
        choices[key] = choice
    }

    fun drop(key: String) {
        choices.remove(key)
    }

    fun dropPlaintext(serverId: String) {
        val prefix = "$serverId|"
        choices.entries.removeIf { it.key.startsWith(prefix) && !it.value.secure }
    }

    fun invalidate(serverId: String) {
        val prefix = "$serverId|"
        choices.keys.filter { it.startsWith(prefix) }.forEach { choices.remove(it) }
    }

    fun reset() {
        choices.clear()
    }
}
