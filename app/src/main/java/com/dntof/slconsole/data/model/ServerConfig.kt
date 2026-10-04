// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.model

import kotlinx.serialization.Serializable

/**
 * 一个游戏服务器(即一个 SLDataAPI 实例)的连接配置。
 *
 * 双凭据轨道(与 Web 端 upstream.js 一致):
 * - 数据面(GET /get_sl_data 等)使用 [verifyToken];
 * - 控制面(POST /control/ 开头的端点)使用 [apiKey]。
 */
@Serializable
data class ServerConfig(
    val id: String,
    val label: String = "",
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val verifyToken: String = "",
    val apiKey: String = "",
    /** "http" 或 "ws",对应插件 control_transport 配置。 */
    val controlTransport: String = "http",
    /** 监控轮询间隔(毫秒),实际使用时会钳制到 [MIN_INTERVAL_MS]..[MAX_INTERVAL_MS]。 */
    val refetchIntervalMs: Long = DEFAULT_INTERVAL_MS,
    /** 语音流端口;0 = 自动(从 /get_sl_data 的 voice_port 读取)。服务器 voice_port 非默认值时需手动填写。 */
    val voicePort: Int = 0,
    val createdAt: Long = 0,
) {
    val displayName: String get() = label.ifBlank { host }
    val baseUrl: String get() = "http://$host:$port"
    val wsUrl: String get() = "ws://$host:$port/control"
    val hasControl: Boolean get() = apiKey.isNotBlank()
    val addressText: String get() = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 8081
        const val DEFAULT_INTERVAL_MS = 5000L
        const val MIN_INTERVAL_MS = 3000L
        const val MAX_INTERVAL_MS = 60000L

        fun newId(): String = "srv_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
