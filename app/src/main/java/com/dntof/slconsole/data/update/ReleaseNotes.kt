// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.update

/** 把发布说明里常见的 Markdown 转成可读的纯文本。 */
object ReleaseNotes {
    private val link = Regex("""!?\[([^\]]*)]\(([^)]*)\)""")
    private val emphasis = Regex("""(\*\*|__|\*|_|~~)(\S(?:.*?\S)?)\1""")
    private val code = Regex("`([^`]*)`")
    private val html = Regex("<[^>]+>")

    fun toPlain(markdown: String): String {
        val out = StringBuilder()
        var inFence = false
        for (rawLine in markdown.replace("\r\n", "\n").lines()) {
            var line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                continue
            }
            if (!inFence) {
                val trimmed = line.trimStart()
                val indent = line.length - trimmed.length
                line = when {
                    trimmed.startsWith("#") -> trimmed.trimStart('#').trim()
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                        " ".repeat(indent) + "• " + trimmed.drop(2)
                    trimmed.startsWith(">") -> trimmed.trimStart('>').trim()
                    trimmed.matches(Regex("""[-*_]{3,}""")) -> ""
                    else -> line
                }
                line = link.replace(line) { it.groupValues[1].ifBlank { it.groupValues[2] } }
                line = code.replace(line) { it.groupValues[1] }
                line = emphasis.replace(line) { it.groupValues[2] }
                line = html.replace(line, "")
            }
            out.append(line).append('\n')
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
