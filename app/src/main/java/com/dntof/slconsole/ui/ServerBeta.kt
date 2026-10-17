// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.dntof.slconsole.data.model.ControlBeta
import com.dntof.slconsole.data.repo.ControlRepository

/**
 * 当前服务器控制通道 hello 里的内测能力。
 * 只有 WS 模式才会有 hello；HTTP 或还没连上时按未开启处理，界面保持 2.6.0 的样子。
 */
@Composable
fun rememberServerBeta(): ControlBeta {
    val server = rememberActiveServer()
    val canListen = server != null && server.controlTransport == "ws" && server.hasControl
    val beta by produceState(
        ControlBeta.None,
        server?.id,
        server?.host,
        server?.port,
        server?.apiKey,
        server?.certFingerprint,
        server?.tlsSeen,
        canListen,
    ) {
        val current = server
        if (current == null || current.controlTransport != "ws" || !current.hasControl) {
            value = ControlBeta.None
            return@produceState
        }
        val client = ControlRepository.eventsClient(current)
        client.beta.collect { value = it }
    }
    return beta
}
