// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import com.dntof.slconsole.data.model.ServerConfig
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsIdleRetryTest {
    private val pin =
        "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:" +
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"

    @After
    fun tearDown() {
        TransportSession.reset()
        TransportStatus.reset()
        TlsEvents.reset()
    }

    @Test
    fun tls_retriesTransientIoOnce() = runBlocking {
        listOf(
            IOException("unexpected end of stream"),
            IOException("unexpected end of stream on https://127.0.0.1/", EOFException()),
            SocketException("Connection reset"),
            IOException("connection closed"),
            SocketException("Broken pipe"),
            EOFException(),
        ).forEach { failure ->
            TransportSession.reset()
            TransportStatus.reset()
            TlsEvents.reset()
            val cfg = config()
            var calls = 0
            val result = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, secure ->
                assertTrue(secure)
                calls++
                if (calls == 1) throw failure
                TlsHttp.Outcome.Done("ok")
            }
            val ready = result as TlsHttp.Result.Ready
            assertEquals(failure.toString(), "ok", ready.value)
            assertTrue(failure.toString(), ready.secure)
            assertEquals(failure.toString(), 2, calls)
            assertEquals(pin, cfg.certFingerprint)
            assertTrue(cfg.tlsSeen)
            assertTrue(TlsEvents.seen(cfg.id))
            assertTrue(TransportStatus.servers.value[cfg.id]?.plaintext != true)
        }
    }

    @Test
    fun cachedTls_retriesUnexpectedEofOnce() = runBlocking {
        val cfg = config()
        seedTls(cfg)
        var calls = 0
        val result = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP) { _, secure ->
            assertTrue(secure)
            calls++
            if (calls == 1) throw IOException("unexpected end of stream", EOFException())
            TlsHttp.Outcome.Done("ok")
        }
        val ready = result as TlsHttp.Result.Ready
        assertEquals("ok", ready.value)
        assertTrue(ready.secure)
        assertEquals(2, calls)
        assertEquals(pin, cfg.certFingerprint)
        assertTrue(cfg.tlsSeen)
        assertFalse(TlsEvents.seen(cfg.id))
        assertTrue(TransportStatus.servers.value[cfg.id]?.plaintext != true)
    }

    @Test
    fun cachedTls_secondUnexpectedEofStopsWithoutDowngrade() = runBlocking {
        val cfg = config()
        seedTls(cfg)
        TransportStatus.onEncrypted(cfg.id)
        var calls = 0
        val result = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP) { _, secure ->
            assertTrue(secure)
            calls++
            throw IOException("unexpected end of stream")
        }
        val blocked = result as TlsHttp.Result.Blocked
        assertEquals(2, calls)
        assertTrue(blocked.failure.message, blocked.failure.message.contains("连接中断"))
        assertTrue(blocked.failure.message, blocked.failure.message.contains("unexpected end of stream"))
        assertFalse(blocked.failure.message.contains("加密连接失败"))
        assertTrue(blocked.failure !is TransportFailure.Downgrade)
        assertTrue(blocked.failure !is TransportFailure.Mismatch)
        assertTrue(blocked.failure !is TransportFailure.Tofu)
        assertEquals(pin, cfg.certFingerprint)
        assertTrue(cfg.tlsSeen)
        assertFalse(TlsEvents.seen(cfg.id))
        assertTrue(TransportStatus.servers.value[cfg.id]?.plaintext != true)
    }

    @Test
    fun tls_doesNotRetryPinMismatchTofuOrHandshake() = runBlocking {
        val cfg = config()
        var pinCalls = 0
        val mismatch = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, secure ->
            assertTrue(secure)
            pinCalls++
            throw SSLHandshakeException("handshake").apply { initCause(PinMismatchException(pin, "CC:DD")) }
        }
        assertEquals(1, pinCalls)
        val mismatchFailure = (mismatch as TlsHttp.Result.Blocked).failure as TransportFailure.Mismatch
        assertEquals(pin, mismatchFailure.pinned)
        assertEquals(pin, cfg.certFingerprint)
        assertTrue(cfg.tlsSeen)
        assertTrue(TransportStatus.servers.value[cfg.id]?.plaintext != true)

        TransportSession.reset()
        TransportStatus.reset()
        TlsEvents.reset()
        val fresh = config().copy(certFingerprint = "", tlsSeen = false)
        var tofuCalls = 0
        val tofu = TlsHttp.call<String>(fresh, fresh.port, TransportSession.Channel.HTTP, useCache = false) { _, _ ->
            tofuCalls++
            throw SSLHandshakeException("handshake").apply { initCause(TofuRequiredException(pin)) }
        }
        assertEquals(1, tofuCalls)
        assertEquals(pin, ((tofu as TlsHttp.Result.Blocked).failure as TransportFailure.Tofu).fingerprint)
        assertEquals("", fresh.certFingerprint)
        assertFalse(fresh.tlsSeen)
        assertFalse(TlsEvents.seen(fresh.id))
        assertTrue(TransportStatus.servers.value[fresh.id]?.plaintext != true)

        TransportSession.reset()
        TransportStatus.reset()
        TlsEvents.reset()
        val seen = config()
        var handshakeCalls = 0
        var plainCalls = 0
        val closed = TlsHttp.call(seen, seen.port, TransportSession.Channel.HTTP, useCache = false) { _, secure ->
            if (secure) {
                handshakeCalls++
                throw SSLHandshakeException("connection closed")
            }
            plainCalls++
            TlsHttp.Outcome.Done("plain")
        }
        assertEquals(1, handshakeCalls)
        assertEquals(0, plainCalls)
        assertTrue((closed as TlsHttp.Result.Blocked).failure is TransportFailure.Downgrade)
        assertEquals(pin, seen.certFingerprint)
        assertTrue(seen.tlsSeen)

        TransportSession.reset()
        TransportStatus.reset()
        TlsEvents.reset()
        val streamDuringHandshake = config().copy(tlsSeen = false)
        var streamCalls = 0
        var streamPlain = 0
        val stream = TlsHttp.call(
            streamDuringHandshake,
            streamDuringHandshake.port,
            TransportSession.Channel.HTTP,
            useCache = false,
        ) { _, secure ->
            if (secure) {
                streamCalls++
                throw SSLHandshakeException("unexpected end of stream")
            }
            streamPlain++
            TlsHttp.Outcome.Done("plain")
        }
        assertEquals(1, streamCalls)
        assertEquals(0, streamPlain)
        val streamBlocked = stream as TlsHttp.Result.Blocked
        assertTrue(streamBlocked.failure.message, streamBlocked.failure.message.contains("加密连接失败"))
        assertFalse(streamBlocked.failure.message.contains("连接中断"))
        assertEquals(pin, streamDuringHandshake.certFingerprint)
        assertFalse(streamDuringHandshake.tlsSeen)
        assertFalse(TlsEvents.seen(streamDuringHandshake.id))
        assertTrue(TransportStatus.servers.value[streamDuringHandshake.id]?.plaintext != true)
    }

    @Test
    fun tls_doesNotRetryStatusTimeoutOrRefused() = runBlocking {
        val cfg = config()
        var refused = 0
        val refusedResult = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, _ ->
            refused++
            throw ConnectException("Connection refused")
        }
        assertEquals(1, refused)
        val refusedFailure = (refusedResult as TlsHttp.Result.Blocked).failure
        assertTrue(refusedFailure.message, refusedFailure.message.contains("加密连接失败"))
        assertFalse(refusedFailure.message.contains("连接中断"))

        var timeouts = 0
        val timeoutResult = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, _ ->
            timeouts++
            throw SocketTimeoutException("Read timed out")
        }
        assertEquals(1, timeouts)
        assertTrue((timeoutResult as TlsHttp.Result.Blocked).failure.message.contains("加密连接失败"))

        var httpErrors = 0
        val httpResult = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, secure ->
            assertTrue(secure)
            httpErrors++
            TlsHttp.Outcome.Done("401")
        }
        assertEquals(1, httpErrors)
        assertEquals("401", (httpResult as TlsHttp.Result.Ready).value)

        var serverErrors = 0
        val serverResult = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, _ ->
            serverErrors++
            TlsHttp.Outcome.Done("500")
        }
        assertEquals(1, serverErrors)
        assertEquals("500", (serverResult as TlsHttp.Result.Ready).value)

        var upgrades = 0
        val upgrade = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, _ ->
            upgrades++
            TlsHttp.Outcome.UpgradeRequired
        }
        assertEquals(1, upgrades)
        assertTrue((upgrade as TlsHttp.Result.Blocked).failure is TransportFailure.TlsRequired)
        assertEquals(pin, cfg.certFingerprint)
        assertTrue(cfg.tlsSeen)
    }

    private fun seedTls(config: ServerConfig) {
        val key = TransportSession.key(
            TransportSession.Channel.HTTP,
            config.id,
            config.host,
            config.port,
            config.certFingerprint,
        )
        TransportSession.put(key, TransportSession.Choice(true, TlsClients.http(config.certFingerprint, secure = true)))
    }

    private fun config() = ServerConfig(
        id = "lab",
        host = "127.0.0.1",
        port = 9,
        verifyToken = "token",
        certFingerprint = pin,
        tlsSeen = true,
    )
}
