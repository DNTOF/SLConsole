// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.repo

import com.dntof.slconsole.data.model.FileChunkReadData
import com.dntof.slconsole.data.model.FileChunkWriteData
import com.dntof.slconsole.data.model.FileReadData
import com.dntof.slconsole.data.model.FileStatData
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.remote.FileChunkPiece
import com.dntof.slconsole.data.remote.FileChunks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64
import kotlin.coroutines.coroutineContext

/**
 * 文件读和写。file_chunks 没开，或者内容还放得进单条消息时，走原来的 files/read 和 files/write。
 * 打开之后，大文件改走 stat / read_chunk / write_chunk。取消或失败时会把上传会话 abort 掉。
 */
object FileTransfer {
    sealed interface ReadResult {
        data class Ok(val text: String, val size: Long) : ReadResult
        data class Failed(val failure: ControlRepository.ControlOutcome.Failure) : ReadResult
    }

    sealed interface WriteResult {
        data object Ok : WriteResult
        data class Failed(val failure: ControlRepository.ControlOutcome.Failure) : WriteResult
    }

    suspend fun readText(
        server: ServerConfig,
        path: String,
        knownSize: Long,
        chunked: Boolean,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): ReadResult {
        if (!chunked) return readOnce(server, path)
        val result = readChunked(server, path, knownSize, onProgress)
        // 服务器说有 file_chunks，但分块端点不在（被关掉或版本对不上）时，退回整文件读取。
        if (result is ReadResult.Failed && result.failure.status in ENDPOINT_MISSING) {
            return readOnce(server, path)
        }
        return result
    }

    suspend fun writeText(
        server: ServerConfig,
        path: String,
        content: String,
        chunked: Boolean,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): WriteResult {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > FileChunks.MAX_WRITE_BYTES) {
            return WriteResult.Failed(
                ControlRepository.ControlOutcome.Failure("内容超过 512KB，服务器不会写入"),
            )
        }
        if (!chunked) return writeOnce(server, path, content)
        val result = writeChunked(server, path, bytes, onProgress)
        // 第一块就碰到端点不存在时什么都没写进去，退回整文件写入，由服务器自己判断大小。
        if (result is WriteResult.Failed && result.failure.status in ENDPOINT_MISSING) {
            return writeOnce(server, path, content)
        }
        return result
    }

    /** 分块端点不存在时服务器可能回的状态码。 */
    private val ENDPOINT_MISSING = setOf(404, 405, 501)

    private suspend fun readOnce(server: ServerConfig, path: String): ReadResult {
        return when (val outcome = ControlRepository.call(server, "/control/files/read", pathBody(path))) {
            is ControlRepository.ControlOutcome.Success -> {
                val data = outcome.data?.let {
                    runCatching { AppJson.json.decodeFromJsonElement(FileReadData.serializer(), it) }.getOrNull()
                } ?: return ReadResult.Failed(ControlRepository.ControlOutcome.Failure("文件内容解析失败"))
                ReadResult.Ok(data.content, data.size)
            }
            is ControlRepository.ControlOutcome.Failure -> ReadResult.Failed(outcome)
        }
    }

    private suspend fun readChunked(
        server: ServerConfig,
        path: String,
        knownSize: Long,
        onProgress: (done: Long, total: Long) -> Unit,
    ): ReadResult {
        val webSocket = server.controlTransport == "ws"
        var total = knownSize
        when (val stat = ControlRepository.call(server, "/control/files/stat", pathBody(path))) {
            is ControlRepository.ControlOutcome.Success -> {
                val info = stat.data?.let {
                    runCatching { AppJson.json.decodeFromJsonElement(FileStatData.serializer(), it) }.getOrNull()
                }
                if (info != null && info.size > 0) total = info.size
            }
            is ControlRepository.ControlOutcome.Failure -> return ReadResult.Failed(stat)
        }

        val pieces = ArrayList<FileChunkPiece>()
        var offset = 0L
        var expectedSha: String? = null
        var guard = 0
        while (guard++ < 64) {
            coroutineContext.ensureActive()
            val outcome = ControlRepository.call(
                server,
                "/control/files/read_chunk",
                buildJsonObject {
                    put("path", path)
                    put("offset", offset)
                    put("length", FileChunks.readChunkSize(webSocket))
                },
            )
            val chunk = when (outcome) {
                is ControlRepository.ControlOutcome.Success -> outcome.data?.let {
                    runCatching { AppJson.json.decodeFromJsonElement(FileChunkReadData.serializer(), it) }.getOrNull()
                } ?: return ReadResult.Failed(ControlRepository.ControlOutcome.Failure("分块内容解析失败"))
                is ControlRepository.ControlOutcome.Failure -> return ReadResult.Failed(outcome)
            }
            if (offset == 0L) {
                expectedSha = chunk.sha256?.takeIf { it.isNotBlank() }
                    ?: return ReadResult.Failed(ControlRepository.ControlOutcome.Failure("服务器没有返回 sha256，已放弃这次读取"))
            }
            val raw = decodeBase64(chunk.data)
                ?: return ReadResult.Failed(ControlRepository.ControlOutcome.Failure("分块数据不是合法 base64"))
            pieces += FileChunkPiece(chunk.offset, raw)
            val done = chunk.nextOffset.coerceAtLeast(offset + raw.size)
            if (chunk.size > 0) total = chunk.size
            onProgress(done, total.coerceAtLeast(done))
            if (chunk.eof || raw.isEmpty() && chunk.offset >= total) break
            val next = if (chunk.nextOffset > offset) chunk.nextOffset else offset + raw.size
            if (next == offset) {
                return ReadResult.Failed(ControlRepository.ControlOutcome.Failure("分块没有向前推进"))
            }
            offset = next
        }

        return when (val assembled = FileChunks.assemble(pieces, expectedSha)) {
            is FileChunks.AssembleResult.Ok -> {
                val text = decodeUtf8(assembled.bytes)
                    ?: return ReadResult.Failed(ControlRepository.ControlOutcome.Failure("文件不是 UTF-8 文本，无法编辑"))
                ReadResult.Ok(text, assembled.bytes.size.toLong())
            }
            is FileChunks.AssembleResult.HashMismatch -> ReadResult.Failed(
                ControlRepository.ControlOutcome.Failure("文件校验失败，已丢弃内容"),
            )
            is FileChunks.AssembleResult.Gap -> ReadResult.Failed(
                ControlRepository.ControlOutcome.Failure(assembled.message),
            )
        }
    }

    private suspend fun writeOnce(server: ServerConfig, path: String, content: String): WriteResult {
        return when (
            val outcome = ControlRepository.call(
                server,
                "/control/files/write",
                buildJsonObject {
                    put("path", path)
                    put("content", content)
                },
            )
        ) {
            is ControlRepository.ControlOutcome.Success -> WriteResult.Ok
            is ControlRepository.ControlOutcome.Failure -> WriteResult.Failed(outcome)
        }
    }

    private suspend fun writeChunked(
        server: ServerConfig,
        path: String,
        bytes: ByteArray,
        onProgress: (done: Long, total: Long) -> Unit,
    ): WriteResult {
        val webSocket = server.controlTransport == "ws"
        val pieces = FileChunks.split(bytes, FileChunks.writeChunkSize(webSocket))
        val sha = FileChunks.sha256Hex(bytes)
        var uploadId: String? = null
        try {
            pieces.forEachIndexed { index, piece ->
                coroutineContext.ensureActive()
                val final = index == pieces.lastIndex
                val outcome = ControlRepository.call(
                    server,
                    "/control/files/write_chunk",
                    buildJsonObject {
                        put("path", path)
                        put("action", "append")
                        put("offset", piece.offset)
                        put("data", Base64.getEncoder().encodeToString(piece.bytes))
                        put("final", final)
                        uploadId?.let { put("upload_id", it) }
                        if (final) put("sha256", sha)
                    },
                )
                when (outcome) {
                    is ControlRepository.ControlOutcome.Failure -> {
                        uploadId?.let { abort(server, path, it) }
                        uploadId = null
                        return WriteResult.Failed(outcome)
                    }
                    is ControlRepository.ControlOutcome.Success -> {
                        val progress = outcome.data?.let {
                            runCatching { AppJson.json.decodeFromJsonElement(FileChunkWriteData.serializer(), it) }.getOrNull()
                        }
                        if (progress != null && progress.uploadId.isNotBlank()) uploadId = progress.uploadId
                        val done = progress?.received ?: (piece.offset + piece.bytes.size)
                        onProgress(done, bytes.size.toLong())
                        if (final && progress?.committed == false) {
                            uploadId?.let { abort(server, path, it) }
                            uploadId = null
                            return WriteResult.Failed(ControlRepository.ControlOutcome.Failure("服务器没有完成写入"))
                        }
                    }
                }
            }
            uploadId = null
            return WriteResult.Ok
        } catch (cancelled: CancellationException) {
            val id = uploadId
            if (id != null) {
                withContext(NonCancellable) { abort(server, path, id) }
            }
            throw cancelled
        }
    }

    private suspend fun abort(server: ServerConfig, path: String, uploadId: String) {
        runCatching {
            ControlRepository.call(
                server,
                "/control/files/write_chunk",
                buildJsonObject {
                    put("path", path)
                    put("upload_id", uploadId)
                    put("action", "abort")
                },
            )
        }
    }

    private fun pathBody(path: String): JsonObject = buildJsonObject { put("path", path) }

    private fun decodeBase64(data: String): ByteArray? {
        if (data.isEmpty()) return ByteArray(0)
        return runCatching { Base64.getDecoder().decode(data) }.getOrNull()
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
