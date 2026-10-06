// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.model

/**
 * 主机栏只接受域名、IPv4，或用方括号包起来的 IPv6。
 * 端口写在旁边的端口栏，这里拒绝会改变 URL 含义的字符和协议。
 */
object HostAddress {
    fun problem(raw: String): String? {
        if (raw.any { it.isWhitespace() || it.code < 32 }) {
            return "主机地址不能包含空格或换行"
        }
        val host = raw.trim()
        if (host.isEmpty()) return "主机地址不能为空"
        if (host.any { it in FORBIDDEN }) {
            return "主机只能填写域名、IPv4，或用方括号包起来的 IPv6，不能带 @、/、?、# 或协议"
        }
        val lower = host.lowercase()
        if ("://" in host || lower.startsWith("http:") || lower.startsWith("https:") ||
            lower.startsWith("ws:") || lower.startsWith("wss:") || lower.startsWith("intent:")
        ) {
            return "不要填写协议，只填主机名或 IP"
        }
        if (host.startsWith("[")) {
            if (host.length < 3 || !host.endsWith("]") || !isIpv6(host.substring(1, host.length - 1))) {
                return "IPv6 请写成 [2001:db8::1] 这种带方括号的形式"
            }
            return null
        }
        if (':' in host) {
            return "主机里不要写端口或协议。端口填在旁边的端口栏；IPv6 要用方括号"
        }
        val dotted = host.split('.')
        if (dotted.size == 4 && dotted.all { part -> part.isNotEmpty() && part.all(Char::isDigit) }) {
            return if (isIpv4(host)) {
                null
            } else {
                "IPv4 地址格式不正确，每一段要在 0 到 255，而且不能有前导 0"
            }
        }
        if (HOSTNAME.matches(host)) return null
        return "主机只能是域名、IPv4，或带方括号的 IPv6"
    }

    private const val FORBIDDEN = "@/?#\\%"

    private val HOSTNAME = Regex(
        """^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\.(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*$""",
    )

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return@all false
            if (part.length > 1 && part.startsWith('0')) return@all false
            val value = part.toIntOrNull() ?: return@all false
            value in 0..255
        }
    }

    private fun isIpv6(address: String): Boolean {
        if (address.isEmpty() || address.length > 45 || address.any { it == '%' || it.isWhitespace() }) return false
        val lastColon = address.lastIndexOf(':')
        val rewritten = if (lastColon >= 0 && '.' in address.substring(lastColon + 1)) {
            val tail = address.substring(lastColon + 1)
            if (!isIpv4(tail)) return false
            address.substring(0, lastColon) + ":0:0"
        } else {
            address
        }
        if (rewritten.count { it == ':' } > 7) return false
        val first = rewritten.indexOf("::")
        val last = rewritten.lastIndexOf("::")
        if (first != last) return false
        if (first >= 0) {
            val left = rewritten.substring(0, first).let { if (it.isEmpty()) emptyList() else it.split(':') }
            val right = rewritten.substring(first + 2).let { if (it.isEmpty()) emptyList() else it.split(':') }
            if (left.size + right.size > 7) return false
            return (left + right).all(::isHextet)
        }
        val parts = rewritten.split(':')
        if (parts.size != 8) return false
        return parts.all(::isHextet)
    }

    private fun isHextet(part: String): Boolean =
        part.length in 1..4 && part.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
