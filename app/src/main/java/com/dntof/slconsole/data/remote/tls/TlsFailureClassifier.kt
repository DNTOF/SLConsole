// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 把 TLS 握手失败分成「对端根本不讲 TLS」和「证书有问题」。
 * 只有前者可以考虑明文;超时、拒绝连接、证书错误都不算。
 */
object TlsFailureClassifier {
    fun classify(error: Throwable): TlsDowngrade.Attempt {
        if (find<TofuRequiredException>(error) != null) return TlsDowngrade.Attempt.TOFU
        if (find<PinMismatchException>(error) != null) return TlsDowngrade.Attempt.PIN_MISMATCH
        if (isCertificateProblem(error)) return TlsDowngrade.Attempt.CERTIFICATE
        if (isPlaintextServer(error)) return TlsDowngrade.Attempt.SERVER_PLAINTEXT
        return TlsDowngrade.Attempt.OTHER
    }

    fun tofu(error: Throwable): TofuRequiredException? = find(error)

    fun mismatch(error: Throwable): PinMismatchException? = find(error)

    fun isCertificateProblem(error: Throwable): Boolean {
        var found = false
        walk(error) { t ->
            if (t is TofuRequiredException || t is PinMismatchException) return@walk
            if (t is CertificateException || t is CertPathValidatorException || t is SSLPeerUnverifiedException) {
                found = true
            }
            val msg = t.message?.lowercase().orEmpty()
            if ("trust anchor" in msg || "certpathvalidatorexception" in msg) found = true
        }
        return found
    }

    fun isPlaintextServer(error: Throwable): Boolean {
        if (isCertificateProblem(error)) return false
        if (find<TofuRequiredException>(error) != null || find<PinMismatchException>(error) != null) return false
        var match = false
        walk(error) { t ->
            val msg = t.message?.lowercase().orEmpty()
            if (PLAINTEXT_MARKERS.any { it in msg }) match = true
        }
        return match
    }

    private val PLAINTEXT_MARKERS = listOf(
        "plaintext connection",
        "unrecognized ssl message",
        "unrecognized record",
        "wrong_version_number",
        "wrong version number",
        "packet_length_too_long",
        "packet length too long",
        "not an ssl/tls record",
        "http request",
    )

    private inline fun <reified T : Throwable> find(root: Throwable): T? {
        var found: T? = null
        walk(root) { t ->
            if (found == null && t is T) found = t
        }
        return found
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
}
