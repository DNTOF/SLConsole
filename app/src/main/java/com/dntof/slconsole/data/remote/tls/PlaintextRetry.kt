// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.io.EOFException
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException

/**
 * 明文传输上可以再试一次的连接失败。
 * 对端把连接关掉、重置,或响应还没读完就结束,都算。超时、拒绝连接、证书和 TLS 握手不算。
 */
internal object PlaintextRetry {
    fun isTransient(error: IOException): Boolean {
        if (hasHardStop(error)) return false
        var match = false
        walk(error) { t ->
            if (t is EOFException) match = true
            val msg = t.message?.lowercase().orEmpty()
            if (MARKERS.any { it in msg }) match = true
        }
        return match
    }

    private fun hasHardStop(error: Throwable): Boolean {
        var hard = false
        walk(error) { t ->
            if (t is SSLHandshakeException ||
                t is SSLProtocolException ||
                t is SSLPeerUnverifiedException ||
                t is CertificateException ||
                t is CertPathValidatorException
            ) {
                hard = true
            }
        }
        return hard
    }

    private fun walk(root: Throwable, visit: (Throwable) -> Unit) {
        val pending = ArrayDeque<Throwable>()
        val seen = HashSet<Throwable>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!seen.add(current)) continue
            visit(current)
            current.cause?.let { pending.add(it) }
            current.suppressed.forEach { pending.add(it) }
        }
    }

    private val MARKERS = listOf(
        "unexpected end of stream",
        "connection reset",
        "connection closed",
        "connection abort",
        "broken pipe",
        "socket closed",
    )
}
