// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import com.dntof.slconsole.data.model.ServerConfig
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaintextRetryTest {
    @After
    fun tearDown() {
        TransportSession.reset()
        TransportStatus.reset()
        TlsEvents.reset()
    }

    @Test
    fun transientMarkers_matchStreamCloseButNotStatusOrTls() {
        listOf(
            IOException("unexpected end of stream"),
            IOException("unexpected end of stream on http://10.0.2.2:8081/", EOFException()),
            SocketException("Connection reset"),
            SocketException("Connection reset by peer"),
            IOException("connection closed"),
            SocketException("Software caused connection abort"),
            SocketException("Broken pipe"),
            SocketException("Socket closed"),
            ProtocolException("unexpected end of stream"),
            EOFException(),
        ).forEach { error ->
            assertTrue(error.toString(), PlaintextRetry.isTransient(error))
        }

        listOf(
            ConnectException("Connection refused"),
            ConnectException("Failed to connect to /10.0.2.2:8081"),
            SocketTimeoutException("timeout"),
            SocketTimeoutException("Read timed out"),
            IOException("boom"),
            SSLHandshakeException("connection closed"),
            SSLHandshakeException("unexpected end of stream"),
            SSLHandshakeException("handshake").apply { initCause(PinMismatchException("AA", "BB")) },
            SSLHandshakeException("handshake").apply { initCause(EOFException("SSL peer shut down incorrectly")) },
        ).forEach { error ->
            assertFalse(error.toString(), PlaintextRetry.isTransient(error))
        }
    }

    @Test
    fun cachedPlaintext_retriesTransientIoOnce() = runBlocking {
        listOf(
            IOException("unexpected end of stream"),
            SocketException("Connection reset"),
            IOException("connection closed"),
        ).forEach { failure ->
            TransportSession.reset()
            TransportStatus.reset()
            TlsEvents.reset()
            val cfg = config()
            seedPlain(cfg)
            var calls = 0
            val result = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP) { _, secure ->
                assertFalse(secure)
                calls++
                if (calls == 1) throw failure
                TlsHttp.Outcome.Done("ok")
            }
            val ready = result as TlsHttp.Result.Ready
            assertEquals(failure.toString(), "ok", ready.value)
            assertFalse(ready.secure)
            assertEquals(failure.toString(), 2, calls)
            assertFalse(cfg.tlsSeen)
            assertEquals("", cfg.certFingerprint)
            assertFalse(TlsEvents.seen(cfg.id))
        }
    }

    @Test
    fun cachedPlaintext_secondTransientFailureStops() = runBlocking {
        val cfg = config()
        seedPlain(cfg)
        TransportStatus.onPlaintext(cfg.id)
        var calls = 0
        val result = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP) { _, _ ->
            calls++
            throw IOException("unexpected end of stream")
        }
        val blocked = result as TlsHttp.Result.Blocked
        assertEquals(2, calls)
        assertTrue(blocked.failure.message, blocked.failure.message.contains("无法连接"))
        assertTrue(blocked.failure.message, blocked.failure.message.contains("unexpected end of stream"))
        assertFalse(blocked.failure.message.contains("加密连接失败"))
        assertFalse(TlsEvents.seen(cfg.id))
        assertFalse(cfg.tlsSeen)
        assertEquals("", cfg.certFingerprint)
        assertEquals(true, TransportStatus.servers.value[cfg.id]?.plaintext)
    }

    @Test
    fun cachedPlaintext_doesNotRetryRefusedTimeoutOrHttpResult() = runBlocking {
        val cfg = config()
        seedPlain(cfg)
        var refused = 0
        val refusedResult = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP) { _, _ ->
            refused++
            throw ConnectException("Connection refused")
        }
        assertEquals(1, refused)
        assertTrue((refusedResult as TlsHttp.Result.Blocked).failure.message.contains("无法连接"))

        var timeouts = 0
        val timeoutResult = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP) { _, _ ->
            timeouts++
            throw SocketTimeoutException("Read timed out")
        }
        assertEquals(1, timeouts)
        assertTrue((timeoutResult as TlsHttp.Result.Blocked).failure.message.contains("无法连接"))

        var httpErrors = 0
        val httpResult = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP) { _, secure ->
            assertFalse(secure)
            httpErrors++
            TlsHttp.Outcome.Done("401")
        }
        assertEquals(1, httpErrors)
        assertEquals("401", (httpResult as TlsHttp.Result.Ready).value)

        var other = 0
        val otherResult = TlsHttp.call<String>(cfg, cfg.port, TransportSession.Channel.HTTP) { _, _ ->
            other++
            throw IllegalStateException("nope")
        }
        assertEquals(1, other)
        assertTrue((otherResult as TlsHttp.Result.Blocked).failure.message.contains("nope"))
        assertFalse(TlsEvents.seen(cfg.id))
    }

    @Test
    fun tlsUnexpectedEof_doesNotProbeOrRetryPlaintext() = runBlocking {
        val cfg = config()
        var secureCalls = 0
        var plainCalls = 0
        val result = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, secure ->
            if (secure) {
                secureCalls++
                throw IOException("unexpected end of stream on http://127.0.0.1/", EOFException())
            }
            plainCalls++
            TlsHttp.Outcome.Done("plain")
        }
        assertEquals(1, secureCalls)
        assertEquals(0, plainCalls)
        val blocked = result as TlsHttp.Result.Blocked
        assertTrue(blocked.failure.message, blocked.failure.message.contains("加密连接失败"))
        assertFalse(TlsEvents.seen(cfg.id))
        assertTrue(TransportStatus.servers.value[cfg.id]?.plaintext != true)
        assertEquals("", cfg.certFingerprint)
        assertFalse(cfg.tlsSeen)
    }

    @Test
    fun handshakeEof_retriesThePlaintextProbeOnce() = runBlocking {
        val cfg = config()
        var plainCalls = 0
        val result = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, secure ->
            if (secure) throw SSLHandshakeException("connection closed")
            plainCalls++
            if (plainCalls == 1) throw IOException("unexpected end of stream")
            TlsHttp.Outcome.Done("plain")
        }
        val ready = result as TlsHttp.Result.Ready
        assertEquals("plain", ready.value)
        assertFalse(ready.secure)
        assertEquals(2, plainCalls)
        assertFalse(cfg.tlsSeen)
        assertEquals("", cfg.certFingerprint)
        assertFalse(TlsEvents.seen(cfg.id))
        assertEquals(true, TransportStatus.servers.value[cfg.id]?.plaintext)
    }

    @Test
    fun handshakeFailure_isNotRetriedAsPlaintext() = runBlocking {
        val cfg = config().copy(tlsSeen = true)
        var secureCalls = 0
        var plainCalls = 0
        val result = TlsHttp.call(cfg, cfg.port, TransportSession.Channel.HTTP, useCache = false) { _, secure ->
            if (secure) {
                secureCalls++
                throw SSLHandshakeException("connection closed")
            }
            plainCalls++
            TlsHttp.Outcome.Done("plain")
        }
        assertEquals(1, secureCalls)
        assertEquals(0, plainCalls)
        assertTrue((result as TlsHttp.Result.Blocked).failure is TransportFailure.Downgrade)
        assertEquals("", cfg.certFingerprint)
    }

    @Test(timeout = 20_000)
    fun plaintextHttpClient_dropsIdleConnections() {
        val listen = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            val accepted = listen.accept()
            accepted.use { sock ->
                sock.soTimeout = 2_000
                val input = sock.getInputStream()
                val pending = StringBuilder()
                val one = ByteArray(1)
                while (!pending.endsWith("\r\n\r\n") && pending.length < 8192) {
                    val n = input.read(one)
                    if (n < 0) return@use
                    pending.append(one[0].toInt().toChar())
                }
                val body = """{"success":true}""".toByteArray()
                val output = sock.getOutputStream()
                output.write(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray(),
                )
                output.write(body)
                output.flush()
                Thread.sleep(300)
            }
        }
        worker.isDaemon = true
        worker.start()
        try {
            val http = TlsClients.http(pin = "", secure = false)
            val response = http.newCall(
                Request.Builder().url("http://127.0.0.1:${listen.localPort}/get_sl_data").build(),
            ).execute()
            response.use {
                assertEquals(200, it.code)
                assertTrue(it.body!!.string().contains("success"))
            }
            assertEquals(0, http.connectionPool.idleConnectionCount())
            assertEquals(0, http.connectionPool.connectionCount())
        } finally {
            runCatching { listen.close() }
            worker.join(1_000)
        }
    }

    private fun seedPlain(config: ServerConfig) {
        val key = TransportSession.key(
            TransportSession.Channel.HTTP,
            config.id,
            config.host,
            config.port,
            config.certFingerprint,
        )
        TransportSession.put(key, TransportSession.Choice(false, TlsClients.http("", secure = false)))
    }

    private fun config() = ServerConfig(
        id = "lab",
        host = "127.0.0.1",
        port = 9,
        verifyToken = "token",
    )
}
