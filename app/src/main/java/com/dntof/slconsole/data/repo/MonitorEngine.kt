package com.dntof.slconsole.data.repo

import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.model.ServerData
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.remote.SlHttpClient
import com.dntof.slconsole.util.stripRichText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 活动服务器监控引擎:按配置间隔轮询 GET /get_sl_data。
 * 单例;切换服务器或修改配置时重启轮询循环。
 */
object MonitorEngine {

    sealed interface MonitorState {
        data object Idle : MonitorState
        data class Loading(val serverId: String) : MonitorState
        data class Ready(
            val serverId: String,
            val data: ServerData,
            val raw: JsonObject,
            val fetchedAt: Long,
            val rttMs: Long,
        ) : MonitorState

        data class Error(val serverId: String, val message: String) : MonitorState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = SlHttpClient()
    private var job: Job? = null

    @Volatile
    private var currentServer: ServerConfig? = null

    private val _state = MutableStateFlow<MonitorState>(MonitorState.Idle)
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    fun setActive(server: ServerConfig?) {
        job?.cancel()
        currentServer = server
        if (server == null) {
            _state.value = MonitorState.Idle
            return
        }
        _state.value = MonitorState.Loading(server.id)
        job = scope.launch {
            while (isActive) {
                fetch(server)
                delay(server.refetchIntervalMs.coerceIn(ServerConfig.MIN_INTERVAL_MS, ServerConfig.MAX_INTERVAL_MS))
            }
        }
    }

    /** 立即重新拉取一次(重启轮询循环)。 */
    fun refreshNow() {
        currentServer?.let { setActive(it) }
    }

    private suspend fun fetch(server: ServerConfig) {
        // 轮询全程兜底:任何异常(如服务器返回异常数据)都转为错误状态,绝不击穿进程
        try {
            fetchInternal(server)
        } catch (e: Exception) {
            _state.value = MonitorState.Error(server.id, "请求异常:${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun fetchInternal(server: ServerConfig) {
        val startedAt = System.currentTimeMillis()
        when (val result = http.getData(server, "/get_sl_data")) {
            is SlHttpClient.HttpResult.Success -> {
                val rtt = System.currentTimeMillis() - startedAt
                val data = runCatching {
                    AppJson.json.decodeFromJsonElement(ServerData.serializer(), result.body)
                }.getOrNull()?.let { parsed ->
                    // 剥离游戏服返回文本中的富文本标签(颜色/字号等)
                    parsed.copy(
                        serverName = parsed.serverName?.let { stripRichText(it) },
                        currentPhase = parsed.currentPhase?.let { stripRichText(it) },
                        nukeStatus = parsed.nukeStatus?.let { stripRichText(it) },
                    )
                }
                _state.value = if (data == null) {
                    MonitorState.Error(server.id, "状态数据解析失败,插件版本可能不兼容")
                } else {
                    MonitorState.Ready(server.id, data, result.body, startedAt, rtt)
                }
            }
            is SlHttpClient.HttpResult.Failure -> {
                _state.value = MonitorState.Error(server.id, result.message)
            }
        }
    }
}
