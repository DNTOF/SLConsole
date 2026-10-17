// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import com.dntof.slconsole.data.model.HostAddress
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.tls.TlsHttp
import com.dntof.slconsole.data.remote.tls.TlsRequiredResponse
import com.dntof.slconsole.data.remote.tls.TransportFailure
import com.dntof.slconsole.data.remote.tls.TransportSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.content

/**
 * SLDataAPI HTTP 客户端,实现双凭据轨道:
 * - [getData]:数据面 GET,Bearer VerifyToken;
 * - [postControl]:控制面 POST /control/ 开头的端点,Bearer API Key。
 *
 * 先走 HTTPS,只有对端不讲 TLS 才退回 HTTP。证书问题不会降级。
 * 统一响应信封 {success, message, data};自动识别 transport_mismatch 提示。
 */
class SlHttpClient {

    sealed interface HttpResult {
        data class Success(val body: JsonObject, val encrypted: Boolean) : HttpResult
        data class Failure(
            val message: String,
            val status: Int = 0,
            val transportHint: String? = null,
            val tls: TransportFailure? = null,
        ) : HttpResult
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getData(config: ServerConfig, path: String, useCache: Boolean = true): HttpResult =
        execute(config, path, body = null, credential = config.verifyToken, useCache = useCache)

    /**
     * 数据面 GET,保留原始 JSON。适配插件路由 `GET /plugins/<id>/<route>` 不一定包在 success 信封里。
     */
    suspend fun getRaw(config: ServerConfig, path: String): RawHttpResult = withContext(Dispatchers.IO) {
        HostAddress.problem(config.host)?.let { return@withContext RawHttpResult.Failure(it) }
        when (
            val result = TlsHttp.call(config, config.port, TransportSession.Channel.HTTP) { client, secure ->
                onceRaw(client, secure, config, path)
            }
        ) {
            is TlsHttp.Result.Ready -> result.value
            is TlsHttp.Result.Blocked -> RawHttpResult.Failure(result.failure.message)
        }
    }

    sealed interface RawHttpResult {
        data class Success(val body: JsonElement) : RawHttpResult
        data class Failure(val message: String) : RawHttpResult
    }

    suspend fun postControl(config: ServerConfig, path: String, body: JsonObject): HttpResult =
        execute(config, path, body = body.toString(), credential = config.apiKey, useCache = true)

    private suspend fun execute(
        config: ServerConfig,
        path: String,
        body: String?,
        credential: String,
        useCache: Boolean,
    ): HttpResult = withContext(Dispatchers.IO) {
        HostAddress.problem(config.host)?.let { return@withContext HttpResult.Failure(it) }
        when (
            val result = TlsHttp.call(
                config,
                config.port,
                TransportSession.Channel.HTTP,
                useCache = useCache,
            ) { client, secure ->
                once(client, secure, config, path, body, credential)
            }
        ) {
            is TlsHttp.Result.Ready -> result.value
            is TlsHttp.Result.Blocked -> blocked(result.failure)
        }
    }

    private fun once(
        client: OkHttpClient,
        secure: Boolean,
        config: ServerConfig,
        path: String,
        body: String?,
        credential: String,
    ): TlsHttp.Outcome<HttpResult> {
        val safeCredential = credential.filter { it.code in 32..126 }
        val builder = Request.Builder()
            .url("${config.httpOrigin(secure)}$path")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $safeCredential")
        if (body != null) builder.post(body.toRequestBody(jsonMediaType)) else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            val text = readCapped(response, path)
                ?: return TlsHttp.Outcome.Done(HttpResult.Failure(BodyLimits.tooLargeMessage(path)))
            if (TlsRequiredResponse.matches(response.code, text)) {
                return TlsHttp.Outcome.UpgradeRequired
            }
            val obj = runCatching { AppJson.json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            val success = (obj?.get("success").textOrNull()) != "false"
            val parsed = if (response.isSuccessful && success) {
                HttpResult.Success(obj ?: buildJsonObject { put("success", true) }, encrypted = secure)
            } else {
                val dataObj = obj?.get("data") as? JsonObject
                val code = dataObj?.get("code").textOrNull()
                val use = dataObj?.get("use").textOrNull()
                HttpResult.Failure(
                    message = obj?.get("message").textOrNull()
                        ?: when (response.code) {
                            401 -> "鉴权失败,请检查凭据"
                            403 -> "API Key 无权访问该端点。需要 admin 权限时，请在游戏里用 sldataapi apikey create <id> admin 新建一把 Key，并在应用里换上"
                            404 -> "端点不存在(检查传输模式配置)"
                            503 -> "数据接口已关闭(verify_token 无效或强度不足)"
                            else -> "HTTP ${response.code}"
                        },
                    status = response.code,
                    transportHint = if (code == "transport_mismatch") use else null,
                )
            }
            return TlsHttp.Outcome.Done(parsed)
        }
    }

    private fun onceRaw(
        client: OkHttpClient,
        secure: Boolean,
        config: ServerConfig,
        path: String,
    ): TlsHttp.Outcome<RawHttpResult> {
        val safeCredential = config.verifyToken.filter { it.code in 32..126 }
        val request = Request.Builder()
            .url("${config.httpOrigin(secure)}$path")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $safeCredential")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (TlsRequiredResponse.matches(response.code, text)) {
                    return TlsHttp.Outcome.UpgradeRequired
                }
                val element = runCatching { AppJson.json.parseToJsonElement(text) }.getOrNull()
                if (response.isSuccessful && element != null) {
                    return TlsHttp.Outcome.Done(RawHttpResult.Success(element))
                }
                val obj = element as? JsonObject
                return TlsHttp.Outcome.Done(
                    RawHttpResult.Failure(obj?.get("message").textOrNull() ?: "HTTP ${response.code}"),
                )
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            return TlsHttp.Outcome.Done(RawHttpResult.Failure("请求失败:${e.message ?: "未知错误"}"))
        }
    }

    private fun blocked(failure: TransportFailure): HttpResult.Failure {
        return HttpResult.Failure(message = failure.message, tls = failure)
    }

    /** 监控数据和单次文件读取超过上限时返回 null。其它响应仍整段读取。 */
    private fun readCapped(response: okhttp3.Response, path: String): String? {
        val body = response.body ?: return ""
        val limit = BodyLimits.limitFor(path) ?: return body.string()
        val declared = body.contentLength()
        if (declared > limit) return null
        return BodyLimits.readUtf8Capped(body.byteStream(), limit)
    }
}
