package com.dntof.slconsole.util

/**
 * 剥离 SCP:SL 富文本标签:<color=#...> <size=N> <b> <i> <u> 等(与 Web 端 normalize.ts 同规则),
 * 并去首尾空白。用于 server_name / current_phase / nuke_status 等游戏服返回的展示文本。
 */
private val RICH_TAG_RE = Regex(
    "</?(color|size|b|i|u|align|lineheight|mspace|cspace|indent|margin|pos|noparse)[^>]*>",
    RegexOption.IGNORE_CASE,
)

fun stripRichText(raw: String): String = raw.replace(RICH_TAG_RE, "").trim()
