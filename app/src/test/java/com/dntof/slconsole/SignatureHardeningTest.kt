// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole

import androidx.compose.ui.graphics.Color
import com.dntof.slconsole.data.local.SignatureCheck
import com.dntof.slconsole.ui.components.decodeBadgePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest

class SignatureHardeningTest {
    // 测试里只放官方指纹的 SHA-256，不放指纹原文（原文写在 README 里）
    private val fingerprintDigest = "15794d3fd7cd1089403d00aa978f9da2cbecd8cefaa035fe01ab44179f8779d3"

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun officialFingerprintMatchesReadme() {
        assertEquals(fingerprintDigest, sha256Hex(SignatureCheck.officialFingerprint()))
        assertEquals(32 * 3 - 1, SignatureCheck.hex(SignatureCheck.officialFingerprint()).length)
    }

    @Test
    fun officialKeyDecodesPalette() {
        val palette = decodeBadgePalette(SignatureCheck.officialFingerprint())
        assertNotNull(palette)
        assertEquals(17, palette!!.size)
        assertEquals(Color(0xFFF6B8D1), palette[0])
    }

    @Test
    fun wrongKeysDoNotDecode() {
        assertNull(decodeBadgePalette(ByteArray(32)))
        val flipped = SignatureCheck.officialFingerprint().also { it[5] = (it[5].toInt() xor 1).toByte() }
        assertNull(decodeBadgePalette(flipped))
        assertNull(decodeBadgePalette(ByteArray(16)))
    }

    @Test
    fun reportRequiresBothPathsToAgree() {
        val official = SignatureCheck.officialFingerprint()
        val good = SignatureCheck.Report(official.copyOf(), official.copyOf(), official)
        assertEquals(0, good.mismatch)
        val other = ByteArray(32) { 0x11 }
        // 只有一条路径被改成官方值时也不能算官方
        assertEquals(true, SignatureCheck.Report(official.copyOf(), other, official).mismatch > 0)
        assertEquals(true, SignatureCheck.Report(other, official.copyOf(), official).mismatch > 0)
        assertEquals(true, SignatureCheck.Report(null, official.copyOf(), official).mismatch > 0)
        assertNull(decodeBadgePalette(SignatureCheck.Report(official.copyOf(), other, official).key))
    }
}
