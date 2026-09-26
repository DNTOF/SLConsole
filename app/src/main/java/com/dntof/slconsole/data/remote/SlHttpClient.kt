package com.dntof.slconsole.data.remote

import com.dntof.slconsole.data.model.ServerConfig
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
import java.util.concurrent.TimeUnit

private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.content

/**
 * SLDataAPI HTTP 客户端,实现双凭据轨道:
 * - [getData]:数据面 GET,Bearer VerifyToken;
 * - [postControl]:控制面 POST /control/ 开头的端点,Bearer API Key。
 *
 * 统一响应信封 {success, message, data};自动识别 transport_mismatch 提示。
 */
class SlHttpClient(
    connectTimeoutSec: Long = 8,
    readTimeoutSec: Long = 30,
) {
    sealed interface HttpResult {
        data class Success(val body: JsonObject) : HttpResult
        data class Failure(
            val message: String,
            val status: Int = 0,
            val transportHint: String? = null,
        ) : HttpResult
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getData(config: ServerConfig, path: String): HttpResult =
        execute(config, path, body = null, credential = config.verifyToken)

    suspend fun postControl(config: ServerConfig, path: String, body: JsonObject): HttpResult =
        execute(config, path, body = body.toString(), credential = config.apiKey)

    private suspend fun execute(
        config: ServerConfig,
        path: String,
        body: String?,
        credential: String,
    ): HttpResult = withContext(Dispatchers.IO) {
        // 头值只允许可打印 ASCII,控制字符会让 OkHttp 直接抛异常
        val safeCredential = credential.filter { it.code in 32..126 }
        val builder = Request.Builder()
            .url("${config.baseUrl}$path")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $safeCredential")
        if (body != null) builder.post(body.toRequestBody(jsonMediaType)) else builder.get()

        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val obj = runCatching { AppJson.json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                val success = (obj?.get("success").textOrNull()) != "false"
                if (response.isSuccessful && success) {
                    HttpResult.Success(obj ?: buildJsonObject { put("success", true) })
                } else {
                    val dataObj = obj?.get("data") as? JsonObject
                    val code = dataObj?.get("code").textOrNull()
                    val use = dataObj?.get("use").textOrNull()
                    HttpResult.Failure(
                        message = obj?.get("message").textOrNull()
                            ?: when (response.code) {
                                401 -> "鉴权失败,请检查凭据"
                                403 -> "API Key 无权访问该端点"
                                404 -> "端点不存在(检查传输模式配置)"
                                503 -> "数据接口已关闭(verify_token 无效或强度不足)"
                                else -> "HTTP ${response.code}"
                            },
                        status = response.code,
                        transportHint = if (code == "transport_mismatch") use else null,
                    )
                }
            }
        } catch (e: IOException) {
            HttpResult.Failure("无法连接到 ${config.addressText}(${e.message ?: "网络错误"})")
        } catch (e: Exception) {
            HttpResult.Failure("请求失败:${e.message ?: "未知错误"}")
        }
    }
}
