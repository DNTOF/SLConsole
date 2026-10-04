package com.dntof.slconsole.data.model

import com.dntof.slconsole.data.remote.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelloAndActionsTest {
    @Test
    fun helloBeta_readsBothFlags() {
        val hello = AppJson.json.parseToJsonElement(
            """{"type":"hello","version":"2.6.1","beta":["adapted_actions","file_chunks"]}""",
        ) as kotlinx.serialization.json.JsonObject
        val beta = ControlBeta.fromHello(hello)
        assertTrue(beta.adaptedActions)
        assertTrue(beta.fileChunks)
    }

    @Test
    fun helloBeta_missingOrEmpty_isOff() {
        val old = AppJson.json.parseToJsonElement(
            """{"type":"hello","version":"2.6.0"}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals(ControlBeta.None, ControlBeta.fromHello(old))

        val empty = AppJson.json.parseToJsonElement(
            """{"type":"hello","version":"2.6.1","beta":[]}""",
        ) as kotlinx.serialization.json.JsonObject
        assertFalse(ControlBeta.fromHello(empty).adaptedActions)
        assertFalse(ControlBeta.fromHello(empty).fileChunks)
    }

    @Test
    fun helloBeta_ignoresUnknownNames() {
        val hello = AppJson.json.parseToJsonElement(
            """{"type":"hello","beta":["file_chunks","something_else"]}""",
        ) as kotlinx.serialization.json.JsonObject
        val beta = ControlBeta.fromHello(hello)
        assertFalse(beta.adaptedActions)
        assertTrue(beta.fileChunks)
    }

    @Test
    fun adaptedPlugin_parsesActionNames() {
        val plugin = AppJson.json.decodeFromString(
            AdaptedPlugin.serializer(),
            """{"id":"dntof.sample_adapted","name":"Sample","actions":["echo","bump"]}""",
        )
        assertEquals(listOf("echo", "bump"), plugin.actions)
    }

    @Test
    fun adaptedPlugin_withoutActions_staysEmpty() {
        val plugin = AppJson.json.decodeFromString(
            AdaptedPlugin.serializer(),
            """{"id":"dntof.sl_player","name":"SLPlayer","routes":["status"]}""",
        )
        assertTrue(plugin.actions.isEmpty())
        assertEquals(listOf("status"), plugin.routes)
    }
}
