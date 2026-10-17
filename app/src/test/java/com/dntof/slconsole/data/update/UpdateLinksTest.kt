// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLinksTest {
    @Test
    fun acceptsRepositoryHttpsUrls() {
        listOf(
            "https://github.com/DNTOF/SLConsole",
            "https://github.com/DNTOF/SLConsole/releases",
            "https://github.com/DNTOF/SLConsole/releases/download/v1.3.0/app.apk",
            "https://GitHub.com/DNTOF/SLConsole/releases/tag/v1.3.0",
        ).forEach { url ->
            assertTrue(url, UpdateLinks.isOfficialReleaseUrl(url))
        }
    }

    @Test
    fun rejectsOtherSchemesAndHosts() {
        listOf(
            "http://github.com/DNTOF/SLConsole/releases",
            "intent://github.com/DNTOF/SLConsole/#Intent;scheme=https;end",
            "https://github.com/other/SLConsole/releases",
            "https://github.com/DNTOF/SLConsole-evil/releases",
            "https://github.com.evil.com/DNTOF/SLConsole/releases",
            "https://objects.githubusercontent.com/DNTOF/SLConsole/app.apk",
            "https://user@github.com/DNTOF/SLConsole/releases",
            "https://github.com/DNTOF/SLConsole/../Other/releases",
            "https://github.com:4443/DNTOF/SLConsole/releases",
            "file:///tmp/app.apk",
            "",
            null,
        ).forEach { url ->
            assertFalse(url, UpdateLinks.isOfficialReleaseUrl(url))
        }
    }

    @Test
    fun fallsBackWhenChosenUrlIsNotOfficial() {
        assertEquals(
            "https://github.com/DNTOF/SLConsole/releases/download/v1/app.apk",
            UpdateLinks.pageToOpen(
                "https://github.com/DNTOF/SLConsole/releases/download/v1/app.apk",
                "https://example.com",
            ),
        )
        assertEquals(
            UpdateConfig.RELEASES_PAGE,
            UpdateLinks.pageToOpen("intent://open", "https://github.com/DNTOF/SLConsole/releases"),
        )
        assertEquals(
            UpdateConfig.RELEASES_PAGE,
            UpdateLinks.pageToOpen(null, "https://example.com/app.apk"),
        )
    }
}
