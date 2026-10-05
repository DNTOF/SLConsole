// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.update

/**
 * 更新源配置，换源只改这里。
 *
 * - [SOURCE] 选用哪种更新源；
 * - [GITHUB_LATEST_URL] GitHub Releases 的 latest 接口（仓库公开后无需令牌）；
 * - [MANIFEST_URL] 备用的 JSON 清单地址，字段见 [ManifestSource]。
 *
 * 这里不放任何令牌。
 */
object UpdateConfig {
    enum class Kind { GitHubReleases, Manifest }

    val SOURCE: Kind = Kind.GitHubReleases

    const val GITHUB_LATEST_URL = "https://api.github.com/repos/DNTOF/SLConsole/releases/latest"

    /** 备用清单，例如放在 GitHub Pages 或任意静态托管上的 update.json。 */
    const val MANIFEST_URL = "https://dntof.github.io/SLConsole/update.json"

    const val RELEASES_PAGE = "https://github.com/DNTOF/SLConsole/releases"

    /** 自动检查最短间隔：6 小时。 */
    const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    fun createSource(): UpdateSource = when (SOURCE) {
        Kind.GitHubReleases -> GitHubReleaseSource(GITHUB_LATEST_URL)
        Kind.Manifest -> ManifestSource(MANIFEST_URL)
    }
}
