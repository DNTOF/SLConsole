// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 当前各服务器的加密状态,给顶栏横幅和确认对话框用。
 * 测试连接用的临时 id 不写进来,避免盖住真正的服务器。
 */
object TransportStatus {
    sealed class Prompt {
        data class Tofu(val fingerprint: String) : Prompt()
        data class Mismatch(val pinned: String, val presented: String) : Prompt()
    }

    data class ServerUi(
        val plaintext: Boolean = false,
        val prompt: Prompt? = null,
        /** 用户关掉对话框之后还留着,顶栏可以再打开。 */
        val hold: Prompt? = null,
    )

    private val _servers = MutableStateFlow<Map<String, ServerUi>>(emptyMap())
    val servers: StateFlow<Map<String, ServerUi>> = _servers.asStateFlow()

    fun onPlaintext(id: String) {
        if (skip(id)) return
        _servers.update { map ->
            val cur = map[id] ?: ServerUi()
            map + (id to cur.copy(plaintext = true, prompt = null))
        }
    }

    fun onEncrypted(id: String) {
        if (skip(id)) return
        _servers.update { it - id }
    }

    fun onTofu(id: String, fingerprint: String) {
        if (skip(id) || fingerprint.isBlank()) return
        val prompt = Prompt.Tofu(fingerprint)
        _servers.update { map ->
            val cur = map[id] ?: ServerUi()
            val dismissed = cur.prompt == null && cur.hold == prompt
            map + (id to cur.copy(plaintext = false, hold = prompt, prompt = if (dismissed) null else prompt))
        }
    }

    fun onMismatch(id: String, pinned: String, presented: String) {
        if (skip(id)) return
        val prompt = Prompt.Mismatch(pinned, presented)
        _servers.update { map ->
            val cur = map[id] ?: ServerUi()
            val dismissed = cur.prompt == null && cur.hold == prompt
            map + (id to cur.copy(plaintext = false, hold = prompt, prompt = if (dismissed) null else prompt))
        }
    }

    fun dismiss(id: String) {
        _servers.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(prompt = null, hold = cur.prompt ?: cur.hold))
        }
    }

    fun reopen(id: String) {
        _servers.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(prompt = cur.hold))
        }
    }

    fun clear(id: String) {
        _servers.update { it - id }
    }

    fun reset() {
        _servers.value = emptyMap()
    }

    private fun skip(id: String): Boolean = id.isBlank() || id == TlsEvents.TEST_ID
}
