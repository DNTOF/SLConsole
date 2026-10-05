// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.update

/**
 * 语义化版本比较：主.次.修订，可带 `-beta.1` 之类的预发布后缀，`+` 后的构建信息忽略。
 * 同一版本号下，正式版比预发布版新。
 */
object AppVersion {
    private data class Parsed(val core: List<Int>, val pre: List<String>)

    private fun parse(raw: String): Parsed {
        val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val core = clean.substringBefore('-')
        val pre = if ('-' in clean) clean.substringAfter('-') else ""
        val numbers = core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        return Parsed(
            core = (numbers + List(3) { 0 }).take(maxOf(3, numbers.size)),
            pre = if (pre.isEmpty()) emptyList() else pre.split('.'),
        )
    }

    /** a 比 b 新返回正数，相同返回 0。 */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val n = maxOf(pa.core.size, pb.core.size)
        for (i in 0 until n) {
            val c = (pa.core.getOrElse(i) { 0 }).compareTo(pb.core.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        if (pa.pre.isEmpty() && pb.pre.isEmpty()) return 0
        if (pa.pre.isEmpty()) return 1
        if (pb.pre.isEmpty()) return -1
        for (i in 0 until minOf(pa.pre.size, pb.pre.size)) {
            val x = pa.pre[i]
            val y = pb.pre[i]
            val xi = x.toIntOrNull()
            val yi = y.toIntOrNull()
            val c = when {
                xi != null && yi != null -> xi.compareTo(yi)
                xi != null -> -1
                yi != null -> 1
                else -> x.compareTo(y)
            }
            if (c != 0) return c
        }
        return pa.pre.size.compareTo(pb.pre.size)
    }

    /** 远端有 versionCode 时以它为准，否则比较版本名。 */
    fun isNewer(info: UpdateInfo, currentName: String, currentCode: Int): Boolean =
        if (info.versionCode != null) {
            info.versionCode > currentCode ||
                (info.versionCode == currentCode && compare(info.versionName, currentName) > 0)
        } else {
            compare(info.versionName, currentName) > 0
        }
}
