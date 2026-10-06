// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.data.repo.ControlRepository
import kotlinx.coroutines.flow.first

/** 把用户确认过的指纹或重置后的加密锁写进已保存的服务器。 */
object ServerSecurity {
    suspend fun trust(id: String, fingerprint: String) {
        val pin = CertFingerprint.normalize(fingerprint) ?: return
        val current = ServiceLocator.serverStore.serversFlow.first().find { it.id == id } ?: return
        ServiceLocator.serverStore.updateSecurity(id, pin, current.tlsSeen)
        TransportStatus.clear(id)
        ControlRepository.closeServer(id)
    }

    suspend fun resetLatch(id: String) {
        val current = ServiceLocator.serverStore.serversFlow.first().find { it.id == id } ?: return
        ServiceLocator.serverStore.updateSecurity(id, current.certFingerprint, tlsSeen = false)
        TlsEvents.forget(id)
        TransportStatus.clear(id)
        ControlRepository.closeServer(id)
    }
}
