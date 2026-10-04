// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.repo

import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.SlHttpClient
import com.dntof.slconsole.data.remote.WsControlClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 控制操作仓库:按服务器配置把 /control/ 开头的控制调用路由到 HTTP 或 WS 通道,
 * 并管理 WS 客户端(控制调用与事件订阅共用同一条连接)。
 */
object ControlRepository {

    sealed interface ControlOutcome {
        /** data 为原始 JsonElement:举报列表等端点返回的是裸数组,不能假定总是对象。 */
        data class Success(val message: String?, val data: JsonElement?) : ControlOutcome
        data class Failure(
            val message: String,
            val transportHint: String? = null,
            val status: Int = 0,
        ) : ControlOutcome
    }

    private val http = SlHttpClient()
    private val wsClients = LinkedHashMap<String, WsControlClient>()

    suspend fun call(
        server: ServerConfig,
        path: String,
        body: JsonObject = JsonObject(emptyMap()),
    ): ControlOutcome {
        if (!server.hasControl) {
            return ControlOutcome.Failure("未配置 API Key,控制功能不可用(请在服务器设置中填写)")
        }
        return when (server.controlTransport) {
            "ws" -> callOverWs(server, path, body)
            else -> callOverHttp(server, path, body)
        }
    }

    private suspend fun callOverHttp(server: ServerConfig, path: String, body: JsonObject): ControlOutcome {
        return when (val result = http.postControl(server, path, body)) {
            is SlHttpClient.HttpResult.Success -> ControlOutcome.Success(
                message = (result.body["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
                data = result.body["data"],
            )
            is SlHttpClient.HttpResult.Failure -> ControlOutcome.Failure(
                message = if (result.transportHint == "ws") {
                    "服务器要求 WS 控制通道:请在服务器设置中把传输模式改为 WS"
                } else {
                    result.message
                },
                transportHint = result.transportHint,
                status = result.status,
            )
        }
    }

    private suspend fun callOverWs(server: ServerConfig, path: String, body: JsonObject): ControlOutcome {
        val ws = eventsClient(server)
        return when (val result = ws.call(path, body)) {
            is WsControlClient.CallResult.Success -> {
                // WS result.data = 服务端完整响应体 {success, message, data: {...}},
                // 与 HTTP 信封不同,需解包内层 data 才能与 HTTP 路径行为一致(Web 端 hub.ts 同做法)
                val bodyObj = result.data
                if (bodyObj != null && bodyObj["success"] != null && bodyObj["data"] != null) {
                    ControlOutcome.Success(
                        message = (bodyObj["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
                        data = bodyObj["data"],
                    )
                } else {
                    ControlOutcome.Success(result.message, result.data)
                }
            }
            is WsControlClient.CallResult.Failure -> {
                // 握手失败和某一次 call 的 403 不是一回事。call 的 403 要留给界面提示 endpoints_override。
                if (result.fromHandshake) {
                    ControlOutcome.Failure(
                        message = when (result.status) {
                            404 -> "服务器未开放 WS 控制通道(control_transport 为 http),请在服务器设置改回 HTTP"
                            401, 403 -> "API Key 校验失败(HTTP ${result.status})。改过 apikey.config 里的角色会让这把 Key 失效，请在游戏里新建一把再换上"
                            else -> result.message
                        },
                    )
                } else {
                    ControlOutcome.Failure(message = result.message, status = result.status)
                }
            }
        }
    }

    /** 获取(或创建并启动)某服务器的 WS 客户端。 */
    @Synchronized
    fun eventsClient(server: ServerConfig): WsControlClient {
        wsClients[server.id]?.let { return it }
        val client = WsControlClient(server)
        wsClients[server.id] = client
        client.start()
        return client
    }

    /** 服务器配置变更或删除时,丢弃旧客户端。 */
    @Synchronized
    fun closeServer(serverId: String) {
        wsClients.remove(serverId)?.stop()
    }

    /** 只保留列表内的服务器客户端,其余停止并移除。 */
    @Synchronized
    fun retainOnly(keepIds: Set<String>) {
        val stale = wsClients.keys.filterNot { it in keepIds }
        stale.forEach { id -> wsClients.remove(id)?.stop() }
    }

    @Synchronized
    fun closeAll() {
        wsClients.values.forEach { it.stop() }
        wsClients.clear()
    }
}
