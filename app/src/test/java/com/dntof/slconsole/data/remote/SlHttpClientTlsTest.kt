// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.tls.CertFingerprint
import com.dntof.slconsole.data.remote.tls.TlsEvents
import com.dntof.slconsole.data.remote.tls.TransportFailure
import com.dntof.slconsole.data.remote.tls.TransportSession
import com.dntof.slconsole.data.remote.tls.TransportStatus
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlHttpClientTlsTest {
    private val client = SlHttpClient()

    @After
    fun tearDown() {
        TransportSession.reset()
        TransportStatus.reset()
        TlsEvents.reset()
    }

    @Test(timeout = 20_000)
    fun pinnedRsaCert_isAcceptedForAnIp() = runBlocking {
        val held = HeldCertificate.Builder().commonName("sldataapi.local").rsa2048().build()
        val key = held.certificate.publicKey as RSAPublicKey
        assertEquals(2048, key.modulus.bitLength())
        val pin = CertFingerprint.sha256Der(held.certificate.encoded)
        val server = MockWebServer()
        server.useHttps(socketFactory(held), false)
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true,"server_name":"Lab","players_count":1}"""))
            val result = client.getData(config(server, pin), "/get_sl_data", useCache = false)
            assertTrue(result.toString(), result is SlHttpClient.HttpResult.Success)
            val success = result as SlHttpClient.HttpResult.Success
            assertTrue(success.encrypted)
            assertEquals("Lab", (success.body["server_name"] as kotlinx.serialization.json.JsonPrimitive).content)
            val recorded = server.takeRequest()
            assertTrue(recorded.handshake != null)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test(timeout = 20_000)
    fun plaintextServer_fallsBack() = runBlocking {
        // MockWebServer 会把 TLS ClientHello 当成还没读完的 HTTP 请求一直等。
        // 旧插件 / tls_mode off 会马上回一段明文，JSSE 才报 unrecognized record。
        val server = ImmediatePlainServer()
        server.nextBody = """{"success":true,"server_name":"Old"}"""
        try {
            val result = client.getData(config(server.port, pin = ""), "/get_sl_data", useCache = false)
            assertTrue(result.toString(), result is SlHttpClient.HttpResult.Success)
            assertTrue(!(result as SlHttpClient.HttpResult.Success).encrypted)
            assertEquals(1, server.httpHits.get())
        } finally {
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun pinMismatch_doesNotSendTheRequest() = runBlocking {
        val held = HeldCertificate.Builder().commonName("sldataapi.local").build()
        val server = MockWebServer()
        server.useHttps(socketFactory(held), false)
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true}"""))
            val wrong = CertFingerprint.sha256Der(
                HeldCertificate.Builder().commonName("other").build().certificate.encoded,
            )
            val result = client.getData(config(server, wrong), "/get_sl_data", useCache = false)
            assertTrue(result is SlHttpClient.HttpResult.Failure)
            val failure = result as SlHttpClient.HttpResult.Failure
            assertTrue(failure.message, failure.tls is TransportFailure.Mismatch)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test(timeout = 20_000)
    fun firstTlsConnect_asksToCompareTheFingerprint() = runBlocking {
        val held = HeldCertificate.Builder().commonName("sldataapi.local").build()
        val server = MockWebServer()
        server.useHttps(socketFactory(held), false)
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true}"""))
            val result = client.getData(config(server, pin = ""), "/get_sl_data", useCache = false)
            val failure = result as SlHttpClient.HttpResult.Failure
            val tofu = failure.tls as TransportFailure.Tofu
            assertEquals(CertFingerprint.sha256Der(held.certificate.encoded), tofu.fingerprint)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test(timeout = 20_000)
    fun handshakeClosedWithoutTlsBytes_probesPlaintextOnce() = runBlocking {
        val server = SilentClosePlainServer()
        server.nextBody = """{"success":true,"server_name":"Plain"}"""
        val cfg = config(server.port, pin = "")
        try {
            val result = client.getData(cfg, "/get_sl_data", useCache = false)
            assertTrue(result.toString(), result is SlHttpClient.HttpResult.Success)
            val success = result as SlHttpClient.HttpResult.Success
            assertTrue(!success.encrypted)
            assertEquals("Plain", (success.body["server_name"] as kotlinx.serialization.json.JsonPrimitive).content)
            assertEquals(1, server.httpHits.get())
            assertTrue(server.tlsCloses.get() >= 1)
            assertEquals("", cfg.certFingerprint)
            assertFalse(cfg.tlsSeen)
            assertFalse(TlsEvents.seen(cfg.id))
            assertEquals(true, TransportStatus.servers.value[cfg.id]?.plaintext)
        } finally {
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun handshakeClosedWithoutHttpResponse_reportsConnectionFailure() = runBlocking {
        val server = SilentClosePlainServer()
        server.answerHttp = false
        try {
            val result = client.getData(config(server.port, pin = ""), "/get_sl_data", useCache = false)
            val failure = result as SlHttpClient.HttpResult.Failure
            assertTrue(failure.message, failure.message.contains("无法连接"))
            assertFalse(failure.message, failure.message.contains("加密连接失败"))
            assertTrue(server.plainAttempts.get() >= 1)
            assertTrue(TransportStatus.servers.value["lab"]?.plaintext != true)
        } finally {
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun handshakeClosedWhenTlsSeen_doesNotProbe() = runBlocking {
        val server = SilentClosePlainServer()
        try {
            val result = client.getData(
                config(server.port, pin = "").copy(tlsSeen = true),
                "/get_sl_data",
                useCache = false,
            )
            val failure = result as SlHttpClient.HttpResult.Failure
            assertTrue(failure.message, failure.tls is TransportFailure.Downgrade)
            assertEquals(0, server.plainAttempts.get())
            assertEquals(0, server.httpHits.get())
        } finally {
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun tlsSeen_refusesPlaintext() = runBlocking {
        val server = ImmediatePlainServer()
        try {
            val result = client.getData(
                config(server.port, pin = "").copy(tlsSeen = true),
                "/get_sl_data",
                useCache = false,
            )
            val failure = result as SlHttpClient.HttpResult.Failure
            assertTrue(failure.message, failure.tls is TransportFailure.Downgrade)
            assertEquals(0, server.httpHits.get())
        } finally {
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun http426_doesNotLoop() = runBlocking {
        val server = ImmediatePlainServer()
        server.nextStatus = 426
        server.nextBody = """{"success":false,"data":{"code":"tls_required"}}"""
        try {
            val result = client.getData(config(server.port, pin = ""), "/get_sl_data", useCache = false)
            val failure = result as SlHttpClient.HttpResult.Failure
            assertTrue(failure.message, failure.tls is TransportFailure.TlsRequired)
            assertEquals(1, server.httpHits.get())
        } finally {
            server.close()
        }
    }

    private fun socketFactory(held: HeldCertificate) = HandshakeCertificates.Builder()
        .heldCertificate(held)
        .build()
        .sslSocketFactory()

    private fun config(server: MockWebServer, pin: String) = config(server.port, pin)

    private fun config(port: Int, pin: String) = ServerConfig(
        id = "lab",
        host = "127.0.0.1",
        port = port,
        verifyToken = "token",
        certFingerprint = pin,
    )
}

/**
 * 第一字节是 0x16 时立刻回 HTTP，模拟不讲 TLS 的插件。
 * MockWebServer 会把 ClientHello 一直缓冲，握手不会失败，所以明文回退不用它。
 */
private class ImmediatePlainServer : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int = socket.localPort
    val httpHits = AtomicInteger()
    var nextStatus: Int = 200
    var nextBody: String = """{"success":true}"""

    init {
        Thread({
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                Thread { runCatching { handle(client) } }.apply { isDaemon = true }.start()
            }
        }, "plain-probe").apply { isDaemon = true }.start()
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 3_000
            val input = sock.getInputStream()
            val first = input.read()
            val output = sock.getOutputStream()
            if (first == 0x16) {
                output.write("HTTP/1.1 400 Plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }
            val pending = StringBuilder()
            if (first >= 0) pending.append(first.toChar())
            val one = ByteArray(1)
            while (!pending.endsWith("\r\n\r\n") && pending.length < 8192) {
                val n = input.read(one)
                if (n < 0) break
                pending.append(one[0].toInt().toChar())
            }
            httpHits.incrementAndGet()
            val body = nextBody.toByteArray()
            val reason = if (nextStatus == 426) "Upgrade Required" else "OK"
            output.write(
                "HTTP/1.1 $nextStatus $reason\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    .toByteArray(),
            )
            output.write(body)
            output.flush()
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

/**
 * 第一字节是 ClientHello 时不回任何字节就关掉。
 * 明文 HTTP 才按 SLDataAPI 回一份 JSON。用来覆盖「挂起后关闭、没有 TLS 记录」的握手。
 */
private class SilentClosePlainServer : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int = socket.localPort
    val tlsCloses = AtomicInteger()
    val plainAttempts = AtomicInteger()
    val httpHits = AtomicInteger()
    var answerHttp: Boolean = true
    var nextBody: String = """{"success":true}"""

    init {
        Thread({
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                Thread { runCatching { handle(client) } }.apply { isDaemon = true }.start()
            }
        }, "silent-close").apply { isDaemon = true }.start()
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 2_000
            val input = sock.getInputStream()
            val first = input.read()
            if (first == 0x16) {
                tlsCloses.incrementAndGet()
                return
            }
            plainAttempts.incrementAndGet()
            if (!answerHttp || first < 0) return
            val pending = StringBuilder()
            pending.append(first.toChar())
            val one = ByteArray(1)
            while (!pending.endsWith("\r\n\r\n") && pending.length < 8192) {
                val n = input.read(one)
                if (n < 0) break
                pending.append(one[0].toInt().toChar())
            }
            httpHits.incrementAndGet()
            val body = nextBody.toByteArray()
            val output = sock.getOutputStream()
            output.write(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    .toByteArray(),
            )
            output.write(body)
            output.flush()
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
