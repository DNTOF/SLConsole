// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import java.security.MessageDigest

/** 一块已经按偏移排好的文件内容。偏移是原始字节，不是 base64。 */
data class FileChunkPiece(
    val offset: Long,
    val bytes: ByteArray,
)

/**
 * SLDataAPI 2.6.1 分块文件的纯逻辑：切块、拼回、校验 sha256。
 * 不发网络请求，方便单测。
 */
object FileChunks {
    /** 服务器单块上限（原始字节）。 */
    const val MAX_CHUNK_BYTES = 160 * 1024

    /** write_chunk 一次会话的总上限。 */
    const val MAX_WRITE_BYTES = 512 * 1024

    /**
     * 读一块的请求长度。
     * WS 响应要连同 base64 塞进 256KB 的单帧，所以比 HTTP 更小。
     */
    fun readChunkSize(webSocket: Boolean): Int = if (webSocket) 96 * 1024 else MAX_CHUNK_BYTES

    /**
     * 写一块的原始字节。HTTP 请求体大约 64KB，base64 之后还要留出 JSON。
     */
    fun writeChunkSize(webSocket: Boolean): Int = if (webSocket) 96 * 1024 else 24 * 1024

    /**
     * 单次 files/read 或 files/write 还能安全放下的内容长度。
     * 再大就改走分块。WS 单帧 256KB，HTTP 请求体大约 64KB。
     */
    fun singleShotLimit(webSocket: Boolean): Int = if (webSocket) 120_000 else 32_000

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            out.append(HEX[b.toInt() ushr 4 and 0x0F])
            out.append(HEX[b.toInt() and 0x0F])
        }
        return out.toString()
    }

    fun split(bytes: ByteArray, chunkSize: Int): List<FileChunkPiece> {
        require(chunkSize > 0)
        if (bytes.isEmpty()) return listOf(FileChunkPiece(0, ByteArray(0)))
        val pieces = ArrayList<FileChunkPiece>((bytes.size / chunkSize) + 1)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            pieces += FileChunkPiece(offset.toLong(), bytes.copyOfRange(offset, end))
            offset = end
        }
        return pieces
    }

    fun assemble(pieces: List<FileChunkPiece>, expectedSha256: String?): AssembleResult {
        val sorted = pieces.sortedBy { it.offset }
        var cursor = 0L
        val out = java.io.ByteArrayOutputStream()
        for (piece in sorted) {
            if (piece.offset != cursor) {
                return AssembleResult.Gap("分块偏移不连续：期望 $cursor，收到 ${piece.offset}")
            }
            out.write(piece.bytes)
            cursor += piece.bytes.size
        }
        val bytes = out.toByteArray()
        val actual = sha256Hex(bytes)
        if (!expectedSha256.isNullOrBlank() && !actual.equals(expectedSha256.trim(), ignoreCase = true)) {
            return AssembleResult.HashMismatch(expectedSha256.trim(), actual)
        }
        return AssembleResult.Ok(bytes, actual)
    }

    sealed interface AssembleResult {
        data class Ok(val bytes: ByteArray, val sha256: String) : AssembleResult
        data class HashMismatch(val expected: String, val actual: String) : AssembleResult
        data class Gap(val message: String) : AssembleResult
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
