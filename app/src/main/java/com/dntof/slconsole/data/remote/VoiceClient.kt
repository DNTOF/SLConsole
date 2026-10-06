// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.dntof.slconsole.data.model.HostAddress
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.tls.HelloTlsCheck
import com.dntof.slconsole.data.remote.tls.TlsClients
import com.dntof.slconsole.data.remote.tls.TlsEvents
import com.dntof.slconsole.data.remote.tls.TlsHandshakePolicy
import com.dntof.slconsole.data.remote.tls.TlsHttp
import com.dntof.slconsole.data.remote.tls.TlsMessages
import com.dntof.slconsole.data.remote.tls.TlsRequiredResponse
import com.dntof.slconsole.data.remote.tls.TransportFailure
import com.dntof.slconsole.data.remote.tls.TransportSession
import com.dntof.slconsole.data.remote.tls.TransportStatus
import com.dntof.slconsole.data.remote.tls.shortMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * SLDataAPI 语音转发客户端(直连游戏服)。
 *
 * 协议(v2.6.0):先试 wss://host:voicePort/ws,Bearer API Key 鉴权,证书和数据端口同一张。
 * 文本帧:hello{sampleRate} / speaker{nickname,userid,channel,playerid} / error;
 * 二进制帧:[0]=0x01 [1]=channel [2-3]=playerId(LE) [4-7]=seq [8..]=float32 LE PCM(默认 48kHz 单声道)。
 *
 * 播放:采样直接阻塞写入 AudioTrack 流式缓冲自然节流;静音 = 丢弃二进制帧(与 Web 端一致)。
 */
class VoiceClient(
    private val server: ServerConfig,
    private val voicePort: Int,
) {
    private val host: String get() = server.host
    private val apiKey: String get() = server.apiKey

    private enum class Phase { TRY_TLS, PLAIN, FORCE_TLS, DEAD }
    enum class State { IDLE, CONNECTING, CONNECTED, FAILED, CLOSED }

    data class SpeakerInfo(
        val nickname: String,
        val steamId: String,
        val channels: List<Int>,
        val lastSeen: Long,
    )

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private val attemptGen = AtomicInteger(0)
    private var phase = Phase.TRY_TLS
    private var audioTrack: AudioTrack? = null
    private var sampleRate = 48000
    private val speakerMap = LinkedHashMap<String, SpeakerInfo>()

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _stateDetail = MutableStateFlow("正在连接…")
    val stateDetail: StateFlow<String> = _stateDetail.asStateFlow()

    private val _speakers = MutableStateFlow<List<SpeakerInfo>>(emptyList())
    val speakers: StateFlow<List<SpeakerInfo>> = _speakers.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    @Volatile private var started = false
    @Volatile private var readyOnce = false
    @Volatile private var droppedFrames = 0L

    sealed interface ProbeResult {
        data class Ok(val encrypted: Boolean) : ProbeResult
        data class Fail(val message: String) : ProbeResult
    }

    /** 开始监听前先探测 /status。先试 HTTPS,规则和数据端口相同。 */
    private suspend fun probeStatus(): ProbeResult {
        HostAddress.problem(host)?.let { return ProbeResult.Fail(it) }
        val result = TlsHttp.call(
            server,
            voicePort,
            TransportSession.Channel.VOICE,
            useCache = false,
        ) { client, secure ->
            val response = client.newBuilder()
                .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(
                    Request.Builder()
                        .url(server.voiceStatusUrl(voicePort, secure))
                        .header("Authorization", "Bearer " + apiKey.filter { it.code in 32..126 })
                        .build(),
                ).execute()
            response.use {
                val body = it.body?.string().orEmpty().take(200)
                if (TlsRequiredResponse.matches(it.code, body)) {
                    return@use TlsHttp.Outcome.UpgradeRequired
                }
                val parsed = when {
                    it.isSuccessful -> ProbeResult.Ok(secure)
                    it.code == 404 -> ProbeResult.Fail(
                        "语音端口 $voicePort 可达,但没有 /status 端点 —— 该端口上不是 SLDataAPI 语音服务。" +
                            "请检查:SLDataAPI 配置 voice_enabled: true 且 voice_port 正确(端口被占用时语音服务会启动失败," +
                            "但监控数据仍报告配置的端口);手机需能直连该 TCP 端口。响应:$body"
                    )
                    it.code == 401 -> ProbeResult.Fail("语音端口鉴权失败(HTTP 401):$body")
                    it.code == 101 -> ProbeResult.Fail(
                        "语音端口对普通请求返回了协议切换(HTTP 101)—— 该端口上运行的不是 SLDataAPI 语音服务" +
                            "(常见原因:端口被 QQ/其他程序占用,SLDataAPI 语音服务启动失败)。请改用空闲端口并在服务器设置中更新。"
                    )
                    it.code == 403 -> ProbeResult.Fail(
                        "API Key 未授权语音端点(HTTP 403):$body(需在 apikey.config 的 endpoints_override 放开 voice:/status 与 voice:/ws)"
                    )
                    else -> ProbeResult.Fail("语音端口探测返回 HTTP ${it.code}:$body")
                }
                TlsHttp.Outcome.Done(parsed)
            }
        }
        return when (result) {
            is TlsHttp.Result.Ready -> result.value
            is TlsHttp.Result.Blocked -> ProbeResult.Fail(result.failure.message)
        }
    }

    fun start() {
        if (started) return
        started = true
        phase = Phase.TRY_TLS
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        speakerMap.clear()
        _error.value = null
        _state.value = State.CONNECTING
        _stateDetail.value = "正在探测语音服务(/status)…"
        scope.launch {
            when (val probe = probeStatus()) {
                is ProbeResult.Ok -> {
                    connect()
                    while (scope.isActive && started) {
                        delay(SPEAKER_TIMEOUT_SCAN_MS)
                        cleanupSpeakers()
                    }
                }
                is ProbeResult.Fail -> {
                    _state.value = State.FAILED
                    _stateDetail.value = probe.message
                }
            }
        }
    }

    private fun connect() {
        if (!started || phase == Phase.DEAD) return
        val secure = phase != Phase.PLAIN
        val generation = attemptGen.incrementAndGet()
        _state.value = State.CONNECTING
        _stateDetail.value = if (secure) {
            "正在加密连接 $host:$voicePort/ws…"
        } else {
            "正在以明文连接 $host:$voicePort/ws…"
        }
        val request = Request.Builder()
            .url(server.voiceWebSocket(voicePort, secure))
            .header("Authorization", "Bearer " + apiKey.filter { it.code in 32..126 })
            .build()
        val previous = webSocket
        webSocket = TlsClients.ws(server.certFingerprint, secure)
            .newWebSocket(request, socketListener(generation, secure))
        previous?.cancel()
    }

    private fun socketListener(generation: Int, secure: Boolean) = object : WebSocketListener() {
        private fun current(): Boolean = generation == attemptGen.get() && started

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current()) return
            if (secure) {
                TlsEvents.mark(server.id)
                TransportStatus.onEncrypted(server.id)
            } else {
                TransportStatus.onPlaintext(server.id)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current()) return
            val obj = runCatching { AppJson.json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "hello" -> {
                    val verdict = HelloTlsCheck.check(secure, obj, server.certFingerprint)
                    if (!verdict.ok) {
                        failTerminal(verdict.message ?: TlsMessages.helloTlsMismatch())
                        return
                    }
                    readyOnce = true
                    sampleRate = (obj["sampleRate"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 48000
                    initAudioTrack()
                    _state.value = State.CONNECTED
                    val lock = if (secure) "加密" else "明文"
                    _stateDetail.value = "已连接语音流($sampleRate Hz,$lock)"
                }
                "speaker" -> {
                    val playerId = (obj["playerid"] as? JsonPrimitive)?.content ?: return
                    val channel = (obj["channel"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    synchronized(speakerMap) {
                        val existing = speakerMap[playerId]
                        speakerMap[playerId] = if (existing != null) {
                            if (existing.channels.contains(channel)) existing
                            else existing.copy(channels = existing.channels + channel)
                        } else {
                            SpeakerInfo(
                                nickname = (obj["nickname"] as? JsonPrimitive)?.content ?: "未知",
                                steamId = (obj["userid"] as? JsonPrimitive)?.content ?: "",
                                channels = listOf(channel),
                                lastSeen = System.currentTimeMillis(),
                            )
                        }
                    }
                    publishSpeakers()
                }
                "error" -> _error.value = (obj["error"] as? JsonPrimitive)?.content ?: "未知语音错误"
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!current() || _muted.value) return
            val data = bytes.toByteArray()
            if (data.size <= 8 || data[0] != 0x01.toByte()) return
            val playerId = ((data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)).toString()
            synchronized(speakerMap) {
                speakerMap[playerId]?.let {
                    speakerMap[playerId] = it.copy(lastSeen = System.currentTimeMillis())
                }
            }
            val track = audioTrack ?: run { droppedFrames++; return }
            val samples = FloatArray((data.size - 8) / 4)
            ByteBuffer.wrap(data, 8, samples.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
                .get(samples)
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current() || phase == Phase.DEAD) return
            val bodyText = runCatching { response?.peekBody(200)?.string() }.getOrNull()
            if (!secure && (response?.code == 426 || TlsRequiredResponse.matches(response?.code ?: 0, bodyText))) {
                if (phase == Phase.FORCE_TLS) {
                    failTerminal(TlsMessages.tlsRequired())
                    return
                }
                phase = Phase.FORCE_TLS
                connect()
                return
            }
            if (secure) {
                val seen = server.tlsSeen || TlsEvents.seen(server.id)
                val allow = phase == Phase.TRY_TLS && !seen
                when (val decision = TlsHandshakePolicy.onTlsFailure(t, seen, allow)) {
                    TlsHandshakePolicy.Decision.UsePlaintext -> {
                        phase = Phase.PLAIN
                        TransportStatus.onPlaintext(server.id)
                        connect()
                    }
                    TlsHandshakePolicy.Decision.RetryLater -> {
                        _state.value = State.FAILED
                        _stateDetail.value = TlsMessages.other(t.shortMessage())
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
            _state.value = State.FAILED
            _stateDetail.value = buildString {
                append("连接失败:")
                append(t.shortMessage())
                if (response?.code == 404) {
                    append(" —— 语音端口上没有 /ws 端点,该端口可能不是 SLDataAPI 语音服务")
                    if (!bodyText.isNullOrBlank()) append("(响应:$bodyText)")
                }
            }
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current() || phase == Phase.DEAD) return
            _state.value = State.CLOSED
            _stateDetail.value = "连接已关闭($code)"
            scheduleReconnect()
        }
    }

    private fun publish(failure: TransportFailure) {
        when (failure) {
            is TransportFailure.Tofu -> TransportStatus.onTofu(server.id, failure.fingerprint)
            is TransportFailure.Mismatch -> TransportStatus.onMismatch(server.id, failure.pinned, failure.presented)
            else -> Unit
        }
    }

    private fun failTerminal(message: String) {
        phase = Phase.DEAD
        attemptGen.incrementAndGet()
        _state.value = State.FAILED
        _stateDetail.value = message
        runCatching { webSocket?.cancel() }
    }

    private fun scheduleReconnect() {
        if (!started || phase == Phase.DEAD) return
        scope.launch {
            delay(if (readyOnce) 3000 else 5000)
            connect()
        }
    }

    private fun initAudioTrack() {
        runCatching {
            audioTrack?.release()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
            val bufSize = max(minBuf, sampleRate * 4)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufSize)
                .build()
            audioTrack?.play()
        }.onFailure {
            _error.value = "音频初始化失败:${it.message ?: "未知错误"}"
        }
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
    }

    fun stop() {
        started = false
        phase = Phase.DEAD
        runCatching { webSocket?.cancel() }
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        scope.cancel()
        _state.value = State.IDLE
        _stateDetail.value = "已停止"
    }

    private fun cleanupSpeakers() {
        val now = System.currentTimeMillis()
        var changed = false
        synchronized(speakerMap) {
            val it = speakerMap.entries.iterator()
            while (it.hasNext()) {
                if (now - it.next().value.lastSeen > SPEAKER_ACTIVE_MS) {
                    it.remove()
                    changed = true
                }
            }
        }
        if (changed) publishSpeakers()
    }

    private fun publishSpeakers() {
        synchronized(speakerMap) {
            _speakers.value = speakerMap.values.sortedBy { it.nickname }
        }
    }

    companion object {
        private const val SPEAKER_ACTIVE_MS = 1500L
        private const val SPEAKER_TIMEOUT_SCAN_MS = 500L
    }
}
