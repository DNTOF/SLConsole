package com.dntof.slconsole.data.remote

import kotlinx.serialization.json.Json

/** 全局共享的 JSON 配置(契约字段 snake_case,宽容解析)。 */
object AppJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }
}
