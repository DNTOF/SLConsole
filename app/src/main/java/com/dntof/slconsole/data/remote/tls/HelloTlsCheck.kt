// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 连上之后核对 hello。字段缺失按旧插件处理,不因此断开。
 * 对得上才继续;对不上就断开。明文 hello 可以伪造,所以不能拿它来降级或改指纹。
 */
object HelloTlsCheck {
    data class Verdict(val ok: Boolean, val message: String? = null)

    fun check(connectionEncrypted: Boolean, hello: JsonObject, pin: String?): Verdict {
        val helloTls = bool(hello["tls"])
        val helloFp = (hello["cert_fingerprint"] as? JsonPrimitive)?.takeIf { it.isString }?.content

        if (helloTls != null && helloTls != connectionEncrypted) {
            return Verdict(false, TlsMessages.helloTlsMismatch())
        }
        if (helloFp != null) {
            val presented = CertFingerprint.normalize(helloFp)
            val pinned = CertFingerprint.normalize(pin)
            val differs = pinned == null || presented == null || presented != pinned
            if (connectionEncrypted && differs) {
                return Verdict(false, TlsMessages.helloFingerprintMismatch())
            }
            if (!connectionEncrypted && pinned != null && presented != pinned) {
                return Verdict(false, TlsMessages.helloFingerprintMismatch())
            }
        }
        return Verdict(true)
    }

    private fun bool(element: JsonElement?): Boolean? {
        val primitive = element as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.content.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}
