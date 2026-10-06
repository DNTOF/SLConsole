// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient

/**
 * 游戏服客户端。默认的 [ConnectionSpec.MODERN_TLS] 同时有 TLS 1.3 和 1.2,
 * 并带 ECDHE-RSA-AES-GCM,能对上 UnityTls 的 RSA 证书。不要收成只剩 1.3。
 *
 * 固定指纹的客户端跳过主机名检查(用户多半填 IP)。明文客户端仍用系统信任库。
 * 更新检查用的是另一只客户端,不会走到这里。
 */
object TlsClients {
    val specs: List<ConnectionSpec> = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)

    private val httpPinned = ConcurrentHashMap<String, OkHttpClient>()
    private val wsPinned = ConcurrentHashMap<String, OkHttpClient>()

    private val plainHttp: OkHttpClient by lazy { plain(readMs = 30_000) }
    private val plainWs: OkHttpClient by lazy { plain(readMs = 0) }

    fun http(pin: String?, secure: Boolean): OkHttpClient {
        if (!secure) return plainHttp
        val key = CertFingerprint.normalize(pin).orEmpty()
        return httpPinned.getOrPut(key) { pinned(key.ifEmpty { null }, readMs = 30_000) }
    }

    fun ws(pin: String?, secure: Boolean): OkHttpClient {
        if (!secure) return plainWs
        val key = CertFingerprint.normalize(pin).orEmpty()
        return wsPinned.getOrPut(key) { pinned(key.ifEmpty { null }, readMs = 0) }
    }

    private fun plain(readMs: Long): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionSpecs(specs)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(readMs, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private fun pinned(pin: String?, readMs: Long): OkHttpClient {
        val trust = PinTrustManager(pin)
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf<TrustManager>(trust), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { _, _ -> true }
            .connectionSpecs(specs)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(readMs, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
