// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

/** 远端最新版本的信息。拿不到的字段为 null。 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int?,
    val notes: String,
    /** APK 直链；没有时打开 [pageUrl]。 */
    val apkUrl: String?,
    val pageUrl: String,
    val sizeBytes: Long?,
    /** 小写十六进制，没有冒号。 */
    val sha256: String?,
    val minSdk: Int?,
)

sealed interface FetchResult {
    data class Found(val info: UpdateInfo) : FetchResult

    /** 更新源不存在或不可访问（例如 404），不算错误。 */
    data class Unavailable(val reason: String) : FetchResult

    data class Failed(val message: String) : FetchResult
}

/** 可替换的更新源。 */
interface UpdateSource {
    val label: String
    suspend fun fetchLatest(): FetchResult
}

internal object UpdateHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 成功时返回 JSON 对象；404/410 归为 Unavailable，其它都是 Failed。 */
    suspend fun getJson(url: String, accept: String): Pair<JsonObject?, FetchResult?> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", "SLConsole-UpdateCheck")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 || response.code == 410 ->
                        null to FetchResult.Unavailable(appString(R.string.update_source_update_source_unavailable_http, response.code))
                    response.code == 403 || response.code == 429 ->
                        null to FetchResult.Failed(appString(R.string.update_source_too_many_requests_update_source))
                    !response.isSuccessful ->
                        null to FetchResult.Failed(appString(R.string.update_source_update_source_returned_http, response.code))
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                        if (obj == null) null to FetchResult.Failed(appString(R.string.update_source_update_info_malformed)) else obj to null
                    }
                }
            }
        } catch (e: IOException) {
            null to FetchResult.Failed(appString(R.string.update_source_network_error, e.message ?: e.javaClass.simpleName))
        } catch (e: IllegalArgumentException) {
            null to FetchResult.Failed(appString(R.string.update_source_invalid_update_url))
        }
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

private fun normalizeSha(raw: String?): String? {
    val hex = raw?.substringAfter("sha256:")?.replace(":", "")?.trim()?.lowercase() ?: return null
    return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
}

/** GitHub Releases latest 接口。仓库私有时匿名请求会得到 404，按 Unavailable 处理。 */
class GitHubReleaseSource(private val url: String) : UpdateSource {
    override val label = "GitHub Releases"

    override suspend fun fetchLatest(): FetchResult {
        val (obj, error) = UpdateHttp.getJson(url, "application/vnd.github+json")
        if (obj == null) return error ?: FetchResult.Failed(appString(R.string.update_source_couldnt_get_update_info))
        val tag = obj.str("tag_name") ?: return FetchResult.Failed(appString(R.string.update_source_release_has_no_version_number))
        if ((obj["draft"] as? JsonPrimitive)?.content == "true") {
            return FetchResult.Unavailable(appString(R.string.update_source_latest_release_still_draft))
        }
        val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val apk = assets.firstOrNull { it.str("name")?.endsWith(".apk", ignoreCase = true) == true }
        return FetchResult.Found(
            UpdateInfo(
                versionName = tag.removePrefix("v").removePrefix("V"),
                versionCode = null,
                notes = obj.str("body").orEmpty(),
                apkUrl = apk?.str("browser_download_url"),
                pageUrl = obj.str("html_url") ?: UpdateConfig.RELEASES_PAGE,
                sizeBytes = apk?.long("size"),
                sha256 = normalizeSha(apk?.str("digest")),
                minSdk = null,
            ),
        )
    }
}

/**
 * 简单 JSON 清单：
 * `{"versionName":"1.3.0","versionCode":7,"notes":"...","apkUrl":"https://...","sha256":"...","minSdk":26}`，
 * 另外可选 `size`（字节）和 `pageUrl`。
 */
class ManifestSource(private val url: String) : UpdateSource {
    override val label: String get() = appString(R.string.update_source_update_manifest)

    override suspend fun fetchLatest(): FetchResult {
        val (obj, error) = UpdateHttp.getJson(url, "application/json")
        if (obj == null) return error ?: FetchResult.Failed(appString(R.string.update_source_couldnt_get_update_info))
        val name = obj.str("versionName") ?: return FetchResult.Failed(appString(R.string.update_source_manifest_has_no_versionname))
        return FetchResult.Found(
            UpdateInfo(
                versionName = name.removePrefix("v"),
                versionCode = obj.int("versionCode"),
                notes = obj.str("notes").orEmpty(),
                apkUrl = obj.str("apkUrl"),
                pageUrl = obj.str("pageUrl") ?: UpdateConfig.RELEASES_PAGE,
                sizeBytes = obj.long("size"),
                sha256 = normalizeSha(obj.str("sha256")),
                minSdk = obj.int("minSdk"),
            ),
        )
    }
}
