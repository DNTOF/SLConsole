package com.dntof.slconsole.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    fun duration(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    fun relative(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = (nowMs - thenMs) / 1000
        return when {
            diff < 2 -> "刚刚"
            diff < 60 -> "$diff 秒前"
            diff < 3600 -> "${diff / 60} 分钟前"
            else -> "${diff / 3600} 小时前"
        }
    }

    /** 封禁时间(unix 秒);0 表示永久。 */
    fun banExpiry(unixSeconds: Long): String {
        if (unixSeconds <= 0) return "永久"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(unixSeconds * 1000))
    }

    fun isoTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            val instant = java.time.Instant.parse(iso)
            SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(instant.toEpochMilli()))
        }.getOrDefault(iso.take(19))
    }

    fun bytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        else -> "%.1f MB".format(bytes / 1024f / 1024f)
    }
}
