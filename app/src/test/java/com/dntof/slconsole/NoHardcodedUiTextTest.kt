// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面文字要放进 strings.xml。Kotlin 源码里剩下的中文字面量只能是下面这些:
 * 发给 SLDataAPI 的协议值、用来匹配服务器返回内容的关键字,以及不会显示给用户的内部断言。
 */
class NoHardcodedUiTextTest {
    private val allowed = setOf(
        // 内部断言/异常,不直接显示
        "data/local/CryptoBox.kt" to "Keystore 未返回 GCM IV",
        "data/local/SignatureCheck.kt" to "签名块被截断",
        "data/local/SignatureCheck.kt" to "签名块长度不对",
        "data/remote/tls/CertFingerprint.kt" to "SHA-256 指纹必须是 32 字节",
        // 匹配服务器返回的错误信息
        "data/remote/BetaHints.kt" to "超时",
        "data/remote/BetaHints.kt" to "符号链接",
        // 匹配服务器返回的阵营名和核弹状态
        "ui/components/Widgets.kt" to "D级",
        "ui/components/Widgets.kt" to "混沌",
        "ui/components/Widgets.kt" to "基金会",
        "ui/components/Widgets.kt" to "九尾狐",
        "ui/components/Widgets.kt" to "观众",
        "ui/components/Widgets.kt" to "教程",
        "ui/screens/DashboardScreen.kt" to "D级",
        "ui/screens/DashboardScreen.kt" to "基金会",
        "ui/screens/DashboardScreen.kt" to "观众",
        "ui/screens/DashboardScreen.kt" to "未激活",
        // 作为请求体发给服务器、写进游戏里的默认原因
        "ui/screens/BansScreen.kt" to "违规行为",
        "ui/screens/BansScreen.kt" to "移动端解封",
        "ui/screens/PlayersScreen.kt" to "由管理端移出",
        "ui/screens/PlayersScreen.kt" to "违规行为",
    )

    private val cjk = Regex("[\\u3000-\\u303f\\u4e00-\\u9fff\\uff00-\\uffef]")

    @Test
    fun noChineseLiteralsOutsideAllowList() {
        val root = listOf("src/main/java/com/dntof/slconsole", "app/src/main/java/com/dntof/slconsole")
            .map(::File).first { it.isDirectory }
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            val rel = file.relativeTo(root).invariantSeparatorsPath
            stringLiterals(file.readText())
                .filter { cjk.containsMatchIn(it) && (rel to it) !in allowed }
                .map { "$rel: \"$it\"" }
        }.toList()
        assertTrue("把这些文字移到 strings.xml:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** 粗略的 Kotlin 词法:跳过注释和字符字面量,返回字符串字面量的正文(不含模板里的嵌套字符串)。 */
    private fun stringLiterals(src: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        fun readString(start: Int, raw: Boolean): Int {
            var j = start
            val body = StringBuilder()
            while (j < src.length) {
                if (raw && src.startsWith("\"\"\"", j)) {
                    out += body.toString(); return j + 3
                }
                val c = src[j]
                if (!raw && c == '\\') {
                    body.append(src, j, j + 2); j += 2; continue
                }
                if (!raw && c == '"') {
                    out += body.toString(); return j + 1
                }
                if (c == '$' && j + 1 < src.length && src[j + 1] == '{') {
                    var depth = 0
                    j += 2
                    while (j < src.length) {
                        val d = src[j]
                        if (d == '"') {
                            j = if (src.startsWith("\"\"\"", j)) readString(j + 3, true) else readString(j + 1, false)
                            continue
                        }
                        if (d == '{') depth++
                        if (d == '}') {
                            if (depth == 0) break
                            depth--
                        }
                        j++
                    }
                    j++
                    continue
                }
                body.append(c)
                j++
            }
            return j
        }
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                src.startsWith("/*", i) -> i = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                src.startsWith("\"\"\"", i) -> i = readString(i + 3, true)
                src[i] == '"' -> i = readString(i + 1, false)
                src[i] == '\'' -> {
                    var j = i + 1
                    if (j < src.length && src[j] == '\\') j++
                    i = src.indexOf('\'', j + 1).let { if (it < 0) src.length else it + 1 }
                }
                else -> i++
            }
        }
        return out
    }
}
