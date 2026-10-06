// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import com.dntof.slconsole.data.model.ControlBeta
import com.dntof.slconsole.data.model.HostAddress
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.model.SlEvent
import com.dntof.slconsole.data.remote.tls.HelloTlsCheck
import com.dntof.slconsole.data.remote.tls.TlsClients
import com.dntof.slconsole.data.remote.tls.TlsEvents
import com.dntof.slconsole.data.remote.tls.TlsHandshakePolicy
import com.dntof.slconsole.data.remote.tls.TlsMessages
import com.dntof.slconsole.data.remote.tls.TransportFailure
import com.dntof.slconsole.data.remote.tls.TransportStatus
import com.dntof.slconsole.data.remote.tls.shortMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * SLDataAPI 控制面 WebSocket 客户端(control_transport: "ws" 时使用)。
 *
 * 协议(2.6.0):hello → 就绪;应用层 ping/pong 心跳(25s);
 * call{reqId,path,body} → result{reqId,ok,status,data|message};
 * subscribe_events → event 帧。断线自动重连,重连后自动重新订阅事件。
 * 2.6.1 起 hello 可以带 beta 数组(adapted_actions / file_chunks)。没有这个字段时按 2.6.0 处理。
 * 连接先试 wss。证书不对就停;只有对端不讲 TLS 才退回 ws,并在界面上标明不加密。
 * hello 里的 tls / cert_fingerprint 只用来核对,不能据此降级。
 */
class WsControlClient(private val config: ServerConfig) {

    enum class ConnState { CONNECTING, READY, FAILED, CLOSED }

    sealed interface CallResult {
        data class Success(val data: JsonObject?, val message: String?) : CallResult
        data class Failure(
            val message: String,
            val status: Int = 0,
            /** 状态码来自 WS 握手,而不是某一次 call 的结果。 */
            val fromHandshake: Boolean = false,
        ) : CallResult
    }

    private enum class Phase { TRY_TLS, PLAIN, FORCE_TLS, DEAD }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private val attemptGen = AtomicInteger(0)
    private var phase = Phase.TRY_TLS
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CallResult>>()
    private val seq = AtomicLong(0)

    private val _state = MutableStateFlow(ConnState.CONNECTING)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _stateDetail = MutableStateFlow("正在连接…")
    val stateDetail: StateFlow<String> = _stateDetail.asStateFlow()

    private val _handshakeStatus = MutableStateFlow(0)
    val handshakeStatus: StateFlow<Int> = _handshakeStatus.asStateFlow()

    private val _beta = MutableStateFlow(ControlBeta.None)
    val beta: StateFlow<ControlBeta> = _beta.asStateFlow()

    private val _events = MutableStateFlow<List<SlEvent>>(emptyList())
    val events: StateFlow<List<SlEvent>> = _events.asStateFlow()

    @Volatile private var started = false
    @Volatile private var wantEvents = false
    @Volatile private var readyOnce = false

    fun sameEndpoint(other: ServerConfig): Boolean {
        return config.host == other.host &&
            config.port == other.port &&
            config.apiKey == other.apiKey &&
            config.certFingerprint == other.certFingerprint &&
            config.tlsSeen == other.tlsSeen &&
            config.controlTransport == other.controlTransport
    }

    fun start() {
        if (started) return
        started = true
        connect()
        scope.launch { heartbeatLoop() }
    }

    private fun connect() {
        if (!started || phase == Phase.DEAD) return
        HostAddress.problem(config.host)?.let { problem ->
            failTerminal(problem)
            return
        }
        val secure = phase != Phase.PLAIN
        val generation = attemptGen.incrementAndGet()
        _beta.value = ControlBeta.None
        _state.value = ConnState.CONNECTING
        _stateDetail.value = if (secure) {
            "正在加密连接 ${config.addressText}/control…"
        } else {
            "正在以明文连接 ${config.addressText}/control…"
        }
        val request = Request.Builder()
            .url(config.controlWebSocket(secure))
            .header("Authorization", "Bearer " + config.apiKey.filter { it.code in 32..126 })
            .build()
        val previous = webSocket
        webSocket = TlsClients.ws(config.certFingerprint, secure)
            .newWebSocket(request, socketListener(generation, secure))
        previous?.cancel()
    }

    private fun socketListener(generation: Int, secure: Boolean) = object : WebSocketListener() {
        private fun current(): Boolean = generation == attemptGen.get() && started

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current()) return
            _handshakeStatus.value = response.code
            if (secure) {
                TlsEvents.mark(config.id)
                TransportStatus.onEncrypted(config.id)
            } else {
                TransportStatus.onPlaintext(config.id)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current()) return
            val obj = runCatching { AppJson.json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "hello" -> {
                    val verdict = HelloTlsCheck.check(secure, obj, config.certFingerprint)
                    if (!verdict.ok) {
                        failTerminal(verdict.message ?: TlsMessages.helloTlsMismatch())
                        return
                    }
                    readyOnce = true
                    _beta.value = ControlBeta.fromHello(obj)
                    _state.value = ConnState.READY
                    val lock = if (secure) "加密" else "明文"
                    _stateDetail.value = "已连接 SLDataAPI ${(obj["version"] as? JsonPrimitive)?.content ?: ""}（$lock）"
                    if (wantEvents) sendSubscribe()
                }
                "pong" -> Unit
                "result" -> {
                    val reqId = (obj["reqId"] as? JsonPrimitive)?.content ?: return
                    val ok = (obj["ok"] as? JsonPrimitive)?.content == "true"
                    pending.remove(reqId)?.complete(
                        if (ok) {
                            CallResult.Success(
                                data = obj["data"] as? JsonObject,
                                message = (obj["message"] as? JsonPrimitive)?.content,
                            )
                        } else {
                            CallResult.Failure(
                                message = (obj["message"] as? JsonPrimitive)?.content ?: "调用失败",
                                status = (obj["status"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                            )
                        }
                    )
                }
                "events_subscribed" -> appendSystemEvent("已订阅实时事件")
                "events_unsubscribed" -> appendSystemEvent("已取消订阅实时事件")
                "event" -> {
                    val name = (obj["event"] as? JsonPrimitive)?.content ?: return
                    val event = SlEvent(
                        event = name,
                        utc = (obj["utc"] as? JsonPrimitive)?.content,
                        data = obj["data"] as? JsonObject ?: JsonObject(emptyMap()),
                    )
                    _events.value = (listOf(event) + _events.value).take(MAX_EVENT_CACHE)
                }
                "error" -> appendSystemEvent("服务器错误:${(obj["message"] as? JsonPrimitive)?.content ?: "未知"}")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current() || phase == Phase.DEAD) return
            _handshakeStatus.value = response?.code ?: 0
            if (!secure && response?.code == 426) {
                if (phase == Phase.FORCE_TLS) {
                    failTerminal(TlsMessages.tlsRequired())
                    return
                }
                phase = Phase.FORCE_TLS
                failAllPending("WS 通道改为加密重试")
                connect()
                return
            }
            if (secure) {
                val seen = config.tlsSeen || TlsEvents.seen(config.id)
                val allow = phase == Phase.TRY_TLS && !seen
                when (val decision = TlsHandshakePolicy.onTlsFailure(t, seen, allow)) {
                    TlsHandshakePolicy.Decision.UsePlaintext -> {
                        phase = Phase.PLAIN
                        TransportStatus.onPlaintext(config.id)
                        failAllPending("WS 通道改为明文")
                        connect()
                    }
                    TlsHandshakePolicy.Decision.RetryLater -> {
                        _state.value = ConnState.FAILED
                        _stateDetail.value = TlsMessages.other(t.shortMessage())
                        failAllPending("WS 通道已断开")
                        scheduleReconnect()
                    }
                    TlsHandshakePolicy.Decision.RetryTls -> {
                        phase = Phase.FORCE_TLS
                        connect()
                    }
                    is TlsHandshakePolicy.Decision.Stop -> {
                        publish(decision.failure)
                        failTerminal(decision.failure.message)
                    }
                }
                return
            }
            _state.value = ConnState.FAILED
            _stateDetail.value = "连接失败:${t.shortMessage()}" + (response?.code?.let { "(HTTP $it)" } ?: "")
            failAllPending("WS 通道已断开")
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            if (phase == Phase.DEAD) return
            _state.value = ConnState.CLOSED
            _stateDetail.value = "连接已关闭($code)"
            failAllPending("WS 通道已关闭")
            scheduleReconnect()
        }
    }

    private fun publish(failure: TransportFailure) {
        when (failure) {
            is TransportFailure.Tofu -> TransportStatus.onTofu(config.id, failure.fingerprint)
            is TransportFailure.Mismatch -> TransportStatus.onMismatch(config.id, failure.pinned, failure.presented)
            else -> Unit
        }
    }

    private fun failTerminal(message: String) {
        phase = Phase.DEAD
        attemptGen.incrementAndGet()
        _state.value = ConnState.FAILED
        _stateDetail.value = message
        failAllPending(message)
        runCatching { webSocket?.cancel() }
    }

    private fun scheduleReconnect() {
        if (!started || phase == Phase.DEAD) return
        scope.launch {
            delay(if (readyOnce) 3000 else 5000)
            connect()
        }
    }

    private suspend fun heartbeatLoop() {
        while (scope.isActive && started) {
            delay(HEARTBEAT_MS)
            if (_state.value == ConnState.READY) {
                webSocket?.send("""{"type":"ping"}""")
            }
        }
    }

    private fun sendSubscribe() {
        webSocket?.send("""{"type":"subscribe_events"}""")
    }

    fun subscribeEvents() {
        wantEvents = true
        if (_state.value == ConnState.READY) sendSubscribe()
    }

    fun unsubscribeEvents() {
        wantEvents = false
        if (_state.value == ConnState.READY) webSocket?.send("""{"type":"unsubscribe_events"}""")
    }

    fun clearEvents() {
        _events.value = emptyList()
    }

    suspend fun awaitReady(timeoutMs: Long = 15_000): Boolean {
        if (_state.value == ConnState.READY) return true
        return withTimeoutOrNull(timeoutMs) {
            _state.first { it == ConnState.READY }
            true
        } ?: false
    }

    suspend fun call(path: String, body: JsonObject? = null, timeoutMs: Long = CALL_TIMEOUT_MS): CallResult {
        if (!awaitReady()) {
            return CallResult.Failure(
                "WS 控制通道未就绪:${_stateDetail.value}",
                status = _handshakeStatus.value,
                fromHandshake = true,
            )
        }
        val reqId = "m_${System.currentTimeMillis()}_${seq.incrementAndGet()}"
        val frame = buildJsonObject {
            put("type", "call")
            put("reqId", reqId)
            put("path", path)
            if (body != null) put("body", body)
        }
        val deferred = CompletableDeferred<CallResult>()
        pending[reqId] = deferred
        val sent = webSocket?.send(frame.toString()) ?: false
        if (!sent) {
            pending.remove(reqId)
            return CallResult.Failure("发送失败,WS 通道不可用")
        }
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: run {
            pending.remove(reqId)
            CallResult.Failure("控制调用超时(${timeoutMs / 1000}s)")
        }
    }

    fun stop() {
        started = false
        phase = Phase.DEAD
        failAllPending("客户端已停止")
        runCatching { webSocket?.cancel() }
        scope.cancel()
    }

    private fun failAllPending(message: String) {
        pending.values.forEach { it.complete(CallResult.Failure(message)) }
        pending.clear()
    }

    private fun appendSystemEvent(text: String) {
        _events.value = (
            listOf(
                SlEvent(
                    event = "_sys",
                    data = buildJsonObject { put("text", text) },
                )
            ) + _events.value
            ).take(MAX_EVENT_CACHE)
    }

    companion object {
        private const val HEARTBEAT_MS = 25_000L
        private const val CALL_TIMEOUT_MS = 30_000L
        private const val MAX_EVENT_CACHE = 250
    }
}
