// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertFingerprintTest {
    @Test
    fun format_isUppercaseColonSeparatedSha256() {
        val raw = ByteArray(32) { it.toByte() }
        val formatted = CertFingerprint.format(raw)
        val parts = formatted.split(":")
        assertEquals(32, parts.size)
        assertTrue(parts.all { it.length == 2 && it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } })
        assertEquals("00", parts[0])
        assertEquals("1F", parts[31])
        assertEquals(formatted, CertFingerprint.groupedLines(formatted).replace("\n", ":"))
        assertEquals(4, CertFingerprint.groupedLines(formatted).lines().size)
    }

    @Test
    fun normalize_acceptsCaseSpacesColonsAndDashes() {
        val hex = "3D200AA5369B75A07356A1858C07C9D8D50556254A5DF07A3EC9C8E0AAA75036"
        val canonical = hex.chunked(2).joinToString(":")
        assertEquals(canonical, CertFingerprint.normalize(hex))
        assertEquals(canonical, CertFingerprint.normalize(hex.lowercase()))
        assertEquals(canonical, CertFingerprint.normalize(canonical.lowercase().replace(":", " ")))
        assertEquals(canonical, CertFingerprint.normalize(hex.chunked(2).joinToString("-")))
        assertEquals(canonical, CertFingerprint.normalize("  $canonical\n"))
        assertTrue(CertFingerprint.matches(" $hex ", canonical.lowercase()))
    }

    @Test
    fun normalize_rejectsWrongLengthAndNonHex() {
        val hex = "3D200AA5369B75A07356A1858C07C9D8D50556254A5DF07A3EC9C8E0AAA75036"
        assertNull(CertFingerprint.normalize(null))
        assertNull(CertFingerprint.normalize(""))
        assertNull(CertFingerprint.normalize(hex.drop(2)))
        assertNull(CertFingerprint.normalize(hex + "AA"))
        assertNull(CertFingerprint.normalize("zz" + hex.drop(2)))
        assertFalse(CertFingerprint.matches(hex, hex.drop(2)))
    }
}
