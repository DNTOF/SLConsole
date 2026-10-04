package com.dntof.slconsole.data.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChunksTest {
    @Test
    fun split_isSequentialAndCoversEveryByte() {
        val bytes = ByteArray(100) { it.toByte() }
        val pieces = FileChunks.split(bytes, 30)
        assertEquals(listOf(0L, 30L, 60L, 90L), pieces.map { it.offset })
        assertEquals(listOf(30, 30, 30, 10), pieces.map { it.bytes.size })
        val assembled = FileChunks.assemble(pieces, FileChunks.sha256Hex(bytes))
        assertTrue(assembled is FileChunks.AssembleResult.Ok)
        assertArrayEquals(bytes, (assembled as FileChunks.AssembleResult.Ok).bytes)
    }

    @Test
    fun assemble_checksSha256FromOffsetZero() {
        val bytes = "配置文件".toByteArray(Charsets.UTF_8)
        val pieces = listOf(
            FileChunkPiece(0, bytes.copyOfRange(0, 4)),
            FileChunkPiece(4, bytes.copyOfRange(4, bytes.size)),
        )
        val ok = FileChunks.assemble(pieces, FileChunks.sha256Hex(bytes))
        assertTrue(ok is FileChunks.AssembleResult.Ok)
        assertEquals(FileChunks.sha256Hex(bytes), (ok as FileChunks.AssembleResult.Ok).sha256)

        val bad = FileChunks.assemble(pieces, "ab".repeat(32))
        assertTrue(bad is FileChunks.AssembleResult.HashMismatch)
    }

    @Test
    fun assemble_rejectsAGap() {
        val result = FileChunks.assemble(
            listOf(FileChunkPiece(2, byteArrayOf(1, 2))),
            null,
        )
        assertTrue(result is FileChunks.AssembleResult.Gap)
    }

    @Test
    fun emptyFile_matchesKnownSha256() {
        val sha = FileChunks.sha256Hex(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha)
        val assembled = FileChunks.assemble(listOf(FileChunkPiece(0, ByteArray(0))), sha)
        assertTrue(assembled is FileChunks.AssembleResult.Ok)
    }
}
