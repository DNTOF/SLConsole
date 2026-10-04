package com.dntof.slconsole.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 控制 WS `hello.beta` 里列出的 2.6.1 内测能力。
 * 2.6.0 没有这个字段；2.6.1 没打开开关时数组为空。两种情况都当成没有内测能力。
 */
data class ControlBeta(
    val features: Set<String> = emptySet(),
) {
    val adaptedActions: Boolean get() = ADAPTED_ACTIONS in features
    val fileChunks: Boolean get() = FILE_CHUNKS in features

    companion object {
        const val ADAPTED_ACTIONS = "adapted_actions"
        const val FILE_CHUNKS = "file_chunks"
        val None = ControlBeta()

        fun fromHello(message: JsonObject): ControlBeta {
            val names = when (val beta = message["beta"]) {
                is JsonArray -> beta.mapNotNull { element ->
                    val primitive = element as? JsonPrimitive ?: return@mapNotNull null
                    primitive.content.takeIf { primitive.isString && it.isNotBlank() }
                }
                // 也接受对象写法，例如 {"file_chunks": true}。值不是 true 的不算打开。
                is JsonObject -> beta.filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }.keys.toList()
                else -> return None
            }
            return if (names.isEmpty()) None else ControlBeta(names.toSet())
        }
    }
}
