// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 进程内的「见过加密」闩。配置里的 tlsSeen 是持久的那一份;
 * 这一份让同一次运行里,加密成功后立刻拒绝明文,不用等磁盘写完。
 */
object TlsEvents {
    const val TEST_ID = "test"

    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var sink: (suspend (String) -> Unit)? = null

    fun seen(id: String): Boolean = id in seen

    fun mark(id: String) {
        if (id.isBlank() || id == TEST_ID) return
        if (!seen.add(id)) return
        TransportSession.dropPlaintext(id)
        val persist = sink ?: return
        scope.launch { runCatching { persist(id) } }
    }

    fun forget(id: String) {
        seen.remove(id)
    }

    fun reset() {
        seen.clear()
        sink = null
    }
}
