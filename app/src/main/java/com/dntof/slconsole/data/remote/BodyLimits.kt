// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import java.io.InputStream

/**
 * 单次监控响应和单次文件读取的体积上限。读到上限就停，避免把过大的响应整段放进内存。
 */
object BodyLimits {
    const val MONITOR_MAX_BYTES = 2 * 1024 * 1024
    const val FILE_READ_MAX_BYTES = 512 * 1024

    fun limitFor(path: String): Int? = when (path.substringBefore('?')) {
        "/get_sl_data" -> MONITOR_MAX_BYTES
        "/control/files/read" -> FILE_READ_MAX_BYTES
        else -> null
    }

    fun tooLargeMessage(path: String): String = when (path.substringBefore('?')) {
        "/get_sl_data" -> "监控数据太大，已停止读取。请稍后再试。"
        "/control/files/read" -> "文件响应太大，已停止读取。请改打开较小的文件，或使用分块读取。"
        else -> "响应太大，已停止读取。"
    }

    /** 超过 [maxBytes] 时返回 null，调用方应丢掉已读内容并关闭连接。 */
    fun readUtf8Capped(input: InputStream, maxBytes: Int): String? {
        if (maxBytes < 0) return null
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) return null
            out.write(buffer, 0, count)
            total += count
        }
        return out.toString(Charsets.UTF_8)
    }
}
