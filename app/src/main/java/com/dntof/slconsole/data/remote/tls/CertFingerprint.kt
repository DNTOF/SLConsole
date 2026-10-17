// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.security.MessageDigest

/**
 * 证书指纹 = 整张证书 DER 的 SHA-256。
 * 约定格式:大写十六进制、冒号分隔,例如 `3D:20:0A:...`(32 字节)。
 */
object CertFingerprint {
    fun sha256Der(der: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        return format(digest)
    }

    fun format(raw32: ByteArray): String {
        require(raw32.size == 32) { "SHA-256 指纹必须是 32 字节" }
        return raw32.joinToString(":") { "%02X".format(it) }
    }

    /**
     * 去掉空格、冒号、连字符和换行,统一成大写冒号格式。
     * 不是 32 字节时返回 null。
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val hex = buildString(64) {
            raw.uppercase().forEach { ch ->
                when (ch) {
                    in '0'..'9', in 'A'..'F' -> append(ch)
                    ':', '-', ' ', '\n', '\r', '\t' -> Unit
                    else -> return null
                }
            }
        }
        if (hex.length != 64) return null
        return hex.chunked(2).joinToString(":")
    }

    fun matches(expected: String?, presented: String?): Boolean {
        val left = normalize(expected) ?: return false
        val right = normalize(presented) ?: return false
        return left == right
    }

    /** 每行 8 字节,方便对照控制台。 */
    fun groupedLines(canonical: String): String {
        val parts = canonical.split(":")
        if (parts.size != 32) return canonical
        return parts.chunked(8).joinToString("\n") { it.joinToString(":") }
    }
}
