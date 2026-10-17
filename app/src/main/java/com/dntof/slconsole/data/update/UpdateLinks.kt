// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.update

import java.net.URI

/**
 * 「立即更新」只打开本仓库的 https 页面。其它地址，包括别的协议，都改去发布页。
 */
object UpdateLinks {
    private const val REPO_PATH = "/DNTOF/SLConsole"

    fun pageToOpen(apkUrl: String?, pageUrl: String?): String {
        val chosen = apkUrl?.takeIf { it.isNotBlank() } ?: pageUrl
        return if (isOfficialReleaseUrl(chosen)) chosen!!.trim() else UpdateConfig.RELEASES_PAGE
    }

    fun isOfficialReleaseUrl(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val text = raw.trim()
        if (text.any { it.isWhitespace() || it.code < 32 || it == '\\' || it == '@' }) return false
        val uri = try {
            URI(text)
        } catch (_: Exception) {
            return false
        }
        if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.host?.equals("github.com", ignoreCase = true) != true) return false
        if (uri.port != -1 && uri.port != 443) return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        val path = uri.path ?: return false
        if (path.contains('\\') || path.contains('@')) return false
        val segments = path.split('/')
        if (segments.any { it == "." || it == ".." }) return false
        return path == REPO_PATH || path.startsWith("$REPO_PATH/")
    }
}
