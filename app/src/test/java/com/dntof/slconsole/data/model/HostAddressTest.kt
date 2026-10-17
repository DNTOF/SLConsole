// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.model

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class HostAddressTest {
    @Test
    fun acceptsHostnameAndAddresses() {
        listOf(
            "localhost",
            "example.com",
            "my-server.local",
            "192.168.1.10",
            "0.0.0.0",
            "[::1]",
            "[2001:db8::1]",
            "[::ffff:192.0.2.1]",
        ).forEach { host ->
            assertNull(host, HostAddress.problem(host))
        }
    }

    @Test
    fun rejectsSchemesUserInfoAndExtraCharacters() {
        listOf(
            "",
            " ",
            "bad host",
            "user@example.com",
            "example.com/admin",
            "example.com?x=1",
            "example.com#frag",
            "http://example.com",
            "https://example.com",
            "ws://example.com",
            "intent://example.com",
            "example.com:8081",
            "::1",
            "[::1",
            "[]",
            "[gggg::1]",
            "01.2.3.4",
            "192.168.1.256",
            "-bad.com",
            "bad-.com",
        ).forEach { host ->
            assertNotNull(host, HostAddress.problem(host))
        }
    }
}
