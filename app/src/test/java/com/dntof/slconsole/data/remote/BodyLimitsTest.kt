// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class BodyLimitsTest {
    @Test
    fun limitsOnlyMonitorAndSingleFileRead() {
        assertEquals(BodyLimits.MONITOR_MAX_BYTES, BodyLimits.limitFor("/get_sl_data"))
        assertEquals(BodyLimits.FILE_READ_MAX_BYTES, BodyLimits.limitFor("/control/files/read"))
        assertNull(BodyLimits.limitFor("/control/files/read_chunk"))
        assertNull(BodyLimits.limitFor("/control/player/data"))
    }

    @Test
    fun stopsWhenBodyExceedsCap() {
        val ok = BodyLimits.readUtf8Capped(ByteArrayInputStream("hello".toByteArray()), 5)
        assertEquals("hello", ok)
        val tooBig = BodyLimits.readUtf8Capped(ByteArrayInputStream("hello!".toByteArray()), 5)
        assertNull(tooBig)
    }
}
