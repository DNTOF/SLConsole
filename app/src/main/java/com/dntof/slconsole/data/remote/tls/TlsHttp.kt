// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import com.dntof.slconsole.data.model.ServerConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import com.dntof.slconsole.data.remote.AppJson

/**
 * HTTP 通道:每次新会话先试 HTTPS。只有握手表明对端不讲 TLS,才退回 HTTP。
 * 握手在没有 TLS 记录时结束,而且这台服务器没见过加密:再发一次明文请求。
 * 这次请求拿到 HTTP 响应才记住明文;失败则只报告连不上,不把它说成加密失败。
 * 明文这条路上,连接被对端中途关掉会把同一次请求再发一遍。鉴权、HTTP 状态、证书和握手失败不重试。
 * 明文收到 426 / tls_required 时再试一次 TLS,然后停住,不会来回循环。
 */
object TlsHttp {
    sealed class Outcome<out T> {
        data class Done<T>(val value: T) : Outcome<T>()
        data object UpgradeRequired : Outcome<Nothing>()
    }

    sealed class Result<out T> {
        data class Ready<T>(val value: T, val secure: Boolean) : Result<T>()
        data class Blocked(val failure: TransportFailure) : Result<Nothing>()
    }

    suspend fun <T> call(
        config: ServerConfig,
        port: Int,
        channel: TransportSession.Channel,
        useCache: Boolean = true,
        block: (OkHttpClient, Boolean) -> Outcome<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        val seen = config.tlsSeen || TlsEvents.seen(config.id)
        val key = TransportSession.key(channel, config.id, config.host, port, config.certFingerprint)
        if (useCache) {
            val cached = TransportSession.get(key, seen)
            if (cached != null) {
                return@withContext useCached(config, key, cached, useCache, block)
            }
        }
        attemptTls(config, key, allowFallback = !seen, useCache = useCache, block = block)
    }

    private fun <T> useCached(
        config: ServerConfig,
        key: String,
        cached: TransportSession.Choice,
        useCache: Boolean,
        block: (OkHttpClient, Boolean) -> Outcome<T>,
    ): Result<T> {
        try {
            val outcome = if (cached.secure) {
                block(cached.client, true)
            } else {
                callPlaintext(cached.client, block)
            }
            return when (outcome) {
                is Outcome.Done -> Result.Ready(outcome.value, cached.secure)
                Outcome.UpgradeRequired -> {
                    if (cached.secure) {
                        Result.Blocked(TransportFailure.TlsRequired())
                    } else {
                        TransportSession.drop(key)
                        attemptTls(config, key, allowFallback = false, useCache = useCache, block = block)
                    }
                }
            }
        } catch (e: IOException) {
            if (!cached.secure) {
                return Result.Blocked(TransportFailure.Other("无法连接：${e.shortMessage()}"))
            }
            return stopOrError(config.id, TlsHandshakePolicy.onTlsFailure(e, tlsSeen = true, allowPlaintextFallback = false), e)
        } catch (e: Exception) {
            return Result.Blocked(TransportFailure.Other(TlsMessages.other(e.shortMessage())))
        }
    }

    private fun <T> attemptTls(
        config: ServerConfig,
        key: String,
        allowFallback: Boolean,
        useCache: Boolean,
        block: (OkHttpClient, Boolean) -> Outcome<T>,
    ): Result<T> {
        val seen = config.tlsSeen || TlsEvents.seen(config.id)
        val client = TlsClients.http(config.certFingerprint, secure = true)
        try {
            return when (val outcome = block(client, true)) {
                is Outcome.Done -> {
                    if (useCache) TransportSession.put(key, TransportSession.Choice(true, client))
                    TlsEvents.mark(config.id)
                    TransportStatus.onEncrypted(config.id)
                    Result.Ready(outcome.value, true)
                }
                Outcome.UpgradeRequired -> Result.Blocked(TransportFailure.TlsRequired())
            }
        } catch (e: IOException) {
            return when (val decision = TlsHandshakePolicy.onTlsFailure(e, seen, allowFallback && !seen)) {
                TlsHandshakePolicy.Decision.UsePlaintext,
                TlsHandshakePolicy.Decision.ProbePlaintext,
                -> attemptPlain(config, key, useCache, block)
                TlsHandshakePolicy.Decision.RetryLater ->
                    Result.Blocked(TransportFailure.Other(TlsMessages.other(e.shortMessage())))
                TlsHandshakePolicy.Decision.RetryTls ->
                    Result.Blocked(TransportFailure.TlsRequired())
                is TlsHandshakePolicy.Decision.Stop -> {
                    publish(config.id, decision.failure)
                    Result.Blocked(decision.failure)
                }
            }
        } catch (e: Exception) {
            return Result.Blocked(TransportFailure.Other(TlsMessages.other(e.shortMessage())))
        }
    }

    private fun <T> attemptPlain(
        config: ServerConfig,
        key: String,
        useCache: Boolean,
        block: (OkHttpClient, Boolean) -> Outcome<T>,
    ): Result<T> {
        val client = TlsClients.http(config.certFingerprint, secure = false)
        try {
            return when (val outcome = callPlaintext(client, block)) {
                is Outcome.Done -> {
                    // 明文请求已经拿到 HTTP 响应才记住这条传输。连不上就停在下面的失败里。
                    if (useCache) TransportSession.put(key, TransportSession.Choice(false, client))
                    TransportStatus.onPlaintext(config.id)
                    Result.Ready(outcome.value, false)
                }
                Outcome.UpgradeRequired ->
                    attemptTls(config, key, allowFallback = false, useCache = useCache, block = block)
            }
        } catch (e: IOException) {
            return Result.Blocked(TransportFailure.Other("无法连接：${e.shortMessage()}"))
        } catch (e: Exception) {
            return Result.Blocked(TransportFailure.Other(TlsMessages.other(e.shortMessage())))
        }
    }

    /**
     * 明文调用。连接被对端关掉时,丢掉空闲连接后再发一次。第二次仍失败就抛出去。
     * 只有 [PlaintextRetry] 认定的瞬时 IO 会重试;HTTP 状态码从 [block] 正常返回,不会进这里。
     */
    private fun <T> callPlaintext(
        client: OkHttpClient,
        block: (OkHttpClient, Boolean) -> Outcome<T>,
    ): Outcome<T> {
        try {
            return block(client, false)
        } catch (e: IOException) {
            if (!PlaintextRetry.isTransient(e)) throw e
            client.connectionPool.evictAll()
            return block(client, false)
        }
    }

    private fun stopOrError(serverId: String, decision: TlsHandshakePolicy.Decision, error: IOException): Result<Nothing> {
        return when (decision) {
            is TlsHandshakePolicy.Decision.Stop -> {
                publish(serverId, decision.failure)
                Result.Blocked(decision.failure)
            }
            else -> Result.Blocked(TransportFailure.Other(TlsMessages.other(error.shortMessage())))
        }
    }

    private fun publish(serverId: String, failure: TransportFailure) {
        when (failure) {
            is TransportFailure.Tofu -> TransportStatus.onTofu(serverId, failure.fingerprint)
            is TransportFailure.Mismatch -> TransportStatus.onMismatch(serverId, failure.pinned, failure.presented)
            else -> Unit
        }
    }
}

/** 明文被拒:HTTP 426,正文 data.code = tls_required。没有正文时,426 本身也算。 */
object TlsRequiredResponse {
    fun matches(status: Int, body: String?): Boolean {
        if (status != 426) return false
        if (body.isNullOrBlank()) return true
        val code = runCatching {
            val obj = AppJson.json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
            val data = obj["data"] as? JsonObject ?: return@runCatching null
            (data["code"] as? JsonPrimitive)?.content
        }.getOrNull()
        return code == null || code == "tls_required"
    }
}
