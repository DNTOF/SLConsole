// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.io.EOFException
import java.net.SocketTimeoutException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException

/**
 * 把 TLS 握手失败分成几类:对端回了明文、证书有问题、握手在没有任何 TLS 记录时结束,以及其他。
 * 只有明确的明文记录可以直接回退。连接被关掉或读超时只表示可以再探一次,不是已经确认的明文。
 * 拒绝连接、证书错误、普通 IO 错误都不探。
 */
object TlsFailureClassifier {
    fun classify(error: Throwable): TlsDowngrade.Attempt {
        if (find<TofuRequiredException>(error) != null) return TlsDowngrade.Attempt.TOFU
        if (find<PinMismatchException>(error) != null) return TlsDowngrade.Attempt.PIN_MISMATCH
        if (isCertificateProblem(error)) return TlsDowngrade.Attempt.CERTIFICATE
        if (isPlaintextServer(error)) return TlsDowngrade.Attempt.SERVER_PLAINTEXT
        if (isSilentClose(error)) return TlsDowngrade.Attempt.HANDSHAKE_EOF
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

    /**
     * 握手结束时对端没有给出 TLS 记录:连接关掉、EOF,或握手过程中的读超时。
     * 证书告警、已经读到的 TLS 警报,以及握手之外的读超时都不算。
     */
    fun isSilentClose(error: Throwable): Boolean {
        if (isCertificateProblem(error)) return false
        if (find<TofuRequiredException>(error) != null || find<PinMismatchException>(error) != null) return false
        if (isPlaintextServer(error)) return false
        if (sawTlsAlert(error)) return false
        val handshake = duringHandshake(error)
        var match = false
        walk(error) { t ->
            if (matchesSilent(t, handshake)) match = true
        }
        return match
    }

    private fun sawTlsAlert(error: Throwable): Boolean {
        var alert = false
        walk(error) { t ->
            val msg = t.message?.lowercase().orEmpty()
            if (TLS_ALERT_MARKERS.any { it in msg }) alert = true
        }
        return alert
    }

    private fun duringHandshake(error: Throwable): Boolean {
        var found = false
        walk(error) { t ->
            if (t is SSLHandshakeException || t is SSLProtocolException) found = true
            if (t.stackTrace.any { it.methodName == "startHandshake" }) found = true
        }
        return found
    }

    private fun matchesSilent(t: Throwable, handshake: Boolean): Boolean {
        val msg = t.message?.lowercase().orEmpty()
        if (isConnectFailure(msg)) return false
        if (EOF_PHRASES.any { it in msg }) return true
        if (!handshake) return false
        if (t is EOFException || t is SocketTimeoutException) return true
        if (RESET_PHRASES.any { it in msg }) return true
        return "read timed out" in msg || msg == "timeout" || msg.endsWith("timed out")
    }

    private fun isConnectFailure(msg: String): Boolean {
        return "connection refused" in msg ||
            "failed to connect" in msg ||
            "connect timed out" in msg ||
            "connection timed out" in msg
    }

    private val EOF_PHRASES = listOf(
        "connection closed",
        "ssl peer shut down incorrectly",
        "remote host terminated the handshake",
        "remote host closed connection during handshake",
    )

    private val RESET_PHRASES = listOf(
        "connection reset",
        "broken pipe",
        "software caused connection abort",
        "connection abort",
    )

    private val TLS_ALERT_MARKERS = listOf(
        "alert",
        "handshake_failure",
        "bad_record_mac",
        "decode_error",
        "decrypt_error",
        "protocol_version",
        "insufficient_security",
        "unexpected_message",
        "bad_certificate",
        "certificate_unknown",
        "certificate_expired",
        "certificate_revoked",
        "certificate_required",
        "unknown_ca",
    )

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
