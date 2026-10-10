// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.update.UpdateHttp
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLProtocolException
import kotlinx.serialization.json.JsonObject
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsDowngradeTest {
    @Test
    fun tlsOk_staysEncrypted() {
        assertEquals(
            TlsDowngrade.Step.Encrypted,
            TlsDowngrade.afterTls(TlsDowngrade.Attempt.SUCCESS, tlsSeen = false, allowPlaintextFallback = true),
        )
    }

    @Test
    fun plaintextOnlyServer_fallsBackUntilTlsWasSeen() {
        assertEquals(
            TlsDowngrade.Step.Plaintext,
            TlsDowngrade.afterTls(TlsDowngrade.Attempt.SERVER_PLAINTEXT, tlsSeen = false, allowPlaintextFallback = true),
        )
        assertEquals(
            TlsDowngrade.Stop.DOWNGRADE_LATCH,
            (TlsDowngrade.afterTls(
                TlsDowngrade.Attempt.SERVER_PLAINTEXT,
                tlsSeen = true,
                allowPlaintextFallback = true,
            ) as TlsDowngrade.Step.Halt).stop,
        )
    }

    @Test
    fun certificateProblems_neverFallBack() {
        listOf(
            TlsDowngrade.Attempt.PIN_MISMATCH to TlsDowngrade.Stop.PIN_MISMATCH,
            TlsDowngrade.Attempt.CERTIFICATE to TlsDowngrade.Stop.CERTIFICATE,
            TlsDowngrade.Attempt.TOFU to TlsDowngrade.Stop.TOFU,
        ).forEach { (attempt, stop) ->
            val step = TlsDowngrade.afterTls(attempt, tlsSeen = false, allowPlaintextFallback = true)
            assertEquals(stop, (step as TlsDowngrade.Step.Halt).stop)
        }
    }

    @Test
    fun http426_retriesTlsOnceThenStops() {
        assertEquals(TlsDowngrade.Step.RetryTls, TlsDowngrade.afterPlaintext426(alreadyRetriedTls = false))
        assertEquals(
            TlsDowngrade.Stop.TLS_REQUIRED,
            (TlsDowngrade.afterPlaintext426(alreadyRetriedTls = true) as TlsDowngrade.Step.Halt).stop,
        )
        val secondHandshake = TlsDowngrade.afterTls(
            TlsDowngrade.Attempt.SERVER_PLAINTEXT,
            tlsSeen = false,
            allowPlaintextFallback = false,
        )
        assertEquals(TlsDowngrade.Stop.TLS_REQUIRED, (secondHandshake as TlsDowngrade.Step.Halt).stop)
    }

    @Test
    fun classifier_plaintextRecordIsNotACertificateFailure() {
        val plaintext = SSLHandshakeException("Unrecognized SSL message, plaintext connection?")
        assertEquals(TlsDowngrade.Attempt.SERVER_PLAINTEXT, TlsFailureClassifier.classify(plaintext))

        val wrongVersion = SSLHandshakeException("Handshake failed").apply {
            initCause(SSLProtocolException("WRONG_VERSION_NUMBER"))
        }
        assertEquals(TlsDowngrade.Attempt.SERVER_PLAINTEXT, TlsFailureClassifier.classify(wrongVersion))
        assertEquals(
            TlsHandshakePolicy.Decision.UsePlaintext,
            TlsHandshakePolicy.onTlsFailure(plaintext, tlsSeen = false, allowPlaintextFallback = true),
        )
        val bodyTimeout = SocketTimeoutException("timeout")
        assertEquals(TlsDowngrade.Attempt.OTHER, TlsFailureClassifier.classify(bodyTimeout))
        assertFalse(TlsFailureClassifier.isSilentClose(bodyTimeout))
    }

    @Test
    fun eofOrConnectionClosed_probesOnceWhenTlsWasNotSeen() {
        val samples = listOf(
            SSLHandshakeException("connection closed"),
            SSLHandshakeException("Remote host terminated the handshake").apply {
                initCause(EOFException("SSL peer shut down incorrectly"))
            },
            SSLHandshakeException("Read timed out"),
            SocketException("connection closed"),
            readTimedOutDuringHandshake(),
        )
        samples.forEach { error ->
            assertFalse(error.toString(), TlsFailureClassifier.isPlaintextServer(error))
            assertTrue(error.toString(), TlsFailureClassifier.isSilentClose(error))
            assertEquals(error.toString(), TlsDowngrade.Attempt.HANDSHAKE_EOF, TlsFailureClassifier.classify(error))
            assertEquals(
                error.toString(),
                TlsDowngrade.Step.Probe,
                TlsDowngrade.afterTls(
                    TlsDowngrade.Attempt.HANDSHAKE_EOF,
                    tlsSeen = false,
                    allowPlaintextFallback = true,
                ),
            )
            assertEquals(
                error.toString(),
                TlsHandshakePolicy.Decision.ProbePlaintext,
                TlsHandshakePolicy.onTlsFailure(error, tlsSeen = false, allowPlaintextFallback = true),
            )
        }
        assertEquals(
            TlsDowngrade.Stop.TLS_REQUIRED,
            (TlsDowngrade.afterTls(
                TlsDowngrade.Attempt.HANDSHAKE_EOF,
                tlsSeen = false,
                allowPlaintextFallback = false,
            ) as TlsDowngrade.Step.Halt).stop,
        )
    }

    @Test
    fun eofWhenTlsSeen_refusesTheProbe() {
        val closed = SSLHandshakeException("connection closed")
        val step = TlsDowngrade.afterTls(
            TlsDowngrade.Attempt.HANDSHAKE_EOF,
            tlsSeen = true,
            allowPlaintextFallback = true,
        )
        assertEquals(TlsDowngrade.Stop.DOWNGRADE_LATCH, (step as TlsDowngrade.Step.Halt).stop)
        val decision = TlsHandshakePolicy.onTlsFailure(closed, tlsSeen = true, allowPlaintextFallback = true)
        val stop = decision as TlsHandshakePolicy.Decision.Stop
        assertTrue(stop.failure is TransportFailure.Downgrade)
    }

    @Test
    fun arbitraryIo_isNotPlaintextAndDoesNotProbe() {
        val samples = listOf(
            IOException("boom"),
            ConnectException("Connection refused"),
            ConnectException("Failed to connect to /127.0.0.1:8081"),
            SocketException("Connection reset"),
            SocketTimeoutException("Read timed out"),
            SSLHandshakeException("Received fatal alert: handshake_failure"),
        )
        samples.forEach { error ->
            assertFalse(error.toString(), TlsFailureClassifier.isPlaintextServer(error))
            assertFalse(error.toString(), TlsFailureClassifier.isSilentClose(error))
            assertEquals(error.toString(), TlsDowngrade.Attempt.OTHER, TlsFailureClassifier.classify(error))
            assertEquals(
                error.toString(),
                TlsHandshakePolicy.Decision.RetryLater,
                TlsHandshakePolicy.onTlsFailure(error, tlsSeen = false, allowPlaintextFallback = true),
            )
        }
    }

    /** 栈上留下 startHandshake，和 SSLSocket 握手读超时同一形状。 */
    private fun readTimedOutDuringHandshake(): SocketTimeoutException = startHandshake()

    private fun startHandshake(): SocketTimeoutException = SocketTimeoutException("Read timed out")

    @Test
    fun classifier_pinAndTrustAnchorDoNotLookLikePlaintext() {
        val pin = "AA:BB"
        val mismatch = SSLHandshakeException("handshake").apply {
            initCause(PinMismatchException(pin, "CC:DD"))
        }
        assertEquals(TlsDowngrade.Attempt.PIN_MISMATCH, TlsFailureClassifier.classify(mismatch))
        assertFalse(TlsFailureClassifier.isPlaintextServer(mismatch))
        assertFalse(TlsFailureClassifier.isSilentClose(mismatch))
        val closedPin = SSLHandshakeException("connection closed").apply {
            initCause(PinMismatchException(pin, "CC:DD"))
        }
        assertEquals(TlsDowngrade.Attempt.PIN_MISMATCH, TlsFailureClassifier.classify(closedPin))
        val pinStop = TlsHandshakePolicy.onTlsFailure(closedPin, tlsSeen = false, allowPlaintextFallback = true)
        assertTrue((pinStop as TlsHandshakePolicy.Decision.Stop).failure is TransportFailure.Mismatch)

        val anchor = SSLHandshakeException("handshake").apply {
            initCause(java.security.cert.CertificateException("Trust anchor for certification path not found"))
        }
        assertEquals(TlsDowngrade.Attempt.CERTIFICATE, TlsFailureClassifier.classify(anchor))
        assertFalse(TlsFailureClassifier.isPlaintextServer(anchor))
    }

    @Test
    fun tls426Body_requiresTheStatus() {
        val body = """{"success":false,"data":{"code":"tls_required"}}"""
        assertTrue(TlsRequiredResponse.matches(426, body))
        assertTrue(TlsRequiredResponse.matches(426, null))
        assertFalse(TlsRequiredResponse.matches(200, body))
        assertFalse(TlsRequiredResponse.matches(426, """{"data":{"code":"other"}}"""))
    }

    @Test
    fun specs_keepTls12AndEcdheRsaAesGcm() {
        val modern = ConnectionSpec.MODERN_TLS
        val versions = modern.tlsVersions.orEmpty()
        assertTrue(versions.contains(TlsVersion.TLS_1_2))
        assertTrue(versions.contains(TlsVersion.TLS_1_3))
        val suites = modern.cipherSuites.orEmpty().map { it.javaName }
        assertTrue(suites.contains("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"))
        assertTrue(suites.contains("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"))
        assertEquals(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT), TlsClients.specs)
        assertTrue(UpdateHttp.client.hostnameVerifier.javaClass.name.contains("OkHostnameVerifier"))
    }

    @Test
    fun hello_mismatchDisconnectsAndDoesNotAuthorizeDowngrade() {
        val pin = "3D:20:0A:A5:36:9B:75:A0:73:56:A1:85:8C:07:C9:D8:D5:05:56:25:4A:5D:F0:7A:3E:C9:C8:E0:AA:A7:50:36"
        val other = "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"
        val encryptedOk = hello("""{"type":"hello","tls":true,"cert_fingerprint":"$pin"}""")
        assertTrue(HelloTlsCheck.check(true, encryptedOk, pin).ok)

        val flag = HelloTlsCheck.check(true, hello("""{"type":"hello","tls":false,"cert_fingerprint":"$pin"}"""), pin)
        assertFalse(flag.ok)
        assertTrue(flag.message.orEmpty().contains("不会"))
        assertTrue(flag.message.orEmpty().contains("明文"))

        val fp = HelloTlsCheck.check(true, hello("""{"type":"hello","tls":true,"cert_fingerprint":"$other"}"""), pin)
        assertFalse(fp.ok)
        assertTrue(fp.message.orEmpty().contains("不会"))

        val oldPlugin = hello("""{"type":"hello","version":"2.6.0"}""")
        assertTrue(HelloTlsCheck.check(false, oldPlugin, "").ok)

        val forged = HelloTlsCheck.check(false, hello("""{"type":"hello","tls":true,"cert_fingerprint":"$other"}"""), pin)
        assertFalse(forged.ok)
    }

    @Test
    fun oldConfig_hasNoPinAndTlsSeenFalse() {
        val raw = """[{"id":"srv_old","host":"10.0.2.2","port":8081,"verifyToken":"tok","apiKey":"key"}]"""
        val list = AppJson.json.decodeFromString<List<ServerConfig>>(raw)
        assertEquals("10.0.2.2", list.single().host)
        assertEquals("", list.single().certFingerprint)
        assertFalse(list.single().tlsSeen)
    }

    private fun hello(json: String): JsonObject = AppJson.json.parseToJsonElement(json) as JsonObject
}
