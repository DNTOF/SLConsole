// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.security.cert.CertificateException
import java.security.interfaces.RSAPublicKey
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTrustManagerTest {
    @Test
    fun accept_matchingSelfSignedLeaf_withoutChainValidation() {
        val held = rsaCert("sldataapi.local")
        val pin = CertFingerprint.sha256Der(held.certificate.encoded)
        PinTrustManager(pin).checkServerTrusted(arrayOf(held.certificate), "RSA")
    }

    @Test
    fun reject_wrongPin() {
        val held = rsaCert("sldataapi.local")
        val other = CertFingerprint.sha256Der(rsaCert("other.local").certificate.encoded)
        val error = runCatching {
            PinTrustManager(other).checkServerTrusted(arrayOf(held.certificate), "RSA")
        }.exceptionOrNull()
        assertTrue(error is PinMismatchException)
        val mismatch = error as PinMismatchException
        assertEquals(other, mismatch.expected)
        assertEquals(CertFingerprint.sha256Der(held.certificate.encoded), mismatch.presented)
    }

    @Test
    fun reject_missingPin_isTofuNotATrustDecision() {
        val held = rsaCert("sldataapi.local")
        val pin = CertFingerprint.sha256Der(held.certificate.encoded)
        val error = runCatching {
            PinTrustManager(null).checkServerTrusted(arrayOf(held.certificate), "RSA")
        }.exceptionOrNull()
        assertTrue(error is TofuRequiredException)
        assertEquals(pin, (error as TofuRequiredException).fingerprint)
    }

    @Test
    fun reject_emptyChain() {
        val error = runCatching {
            PinTrustManager(null).checkServerTrusted(emptyArray(), "RSA")
        }.exceptionOrNull()
        assertTrue(error is CertificateException)
        assertTrue(error !is TofuRequiredException)
    }

    private fun rsaCert(name: String): HeldCertificate {
        val held = HeldCertificate.Builder().commonName(name).rsa2048().build()
        val key = held.certificate.publicKey as RSAPublicKey
        assertEquals("RSA", key.algorithm)
        assertEquals(2048, key.modulus.bitLength())
        return held
    }
}
