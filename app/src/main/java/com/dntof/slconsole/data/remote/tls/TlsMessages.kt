// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString


/** 给界面看的短句。指纹正文由对话框另排,不塞进这一句里。 */
object TlsMessages {
    val PLAINTEXT_BANNER: String get() = appString(R.string.tls_messages_connection_not_encrypted)
    val PLAINTEXT_DETAIL: String get() = appString(R.string.tls_messages_current_plugin_doesnt_provide_tls)

    fun tofu() =
        appString(R.string.tls_messages_first_encrypted_connection_server_compare)

    fun mismatch() =
        appString(R.string.tls_messages_certificate_fingerprint_doesnt_match_pinned)

    fun downgrade() =
        appString(R.string.tls_messages_server_has_connected_encryption_before)

    fun tlsRequired() =
        appString(R.string.tls_messages_server_requires_encryption_http_426)

    fun certificate(detail: String) = appString(R.string.tls_messages_certificate_verification_failed_connection_was, detail)

    fun other(detail: String) = appString(R.string.tls_messages_encrypted_connection_failed, detail)

    /** 握手已经完成,之后响应流被掐断。不要把它说成加密失败。 */
    fun interrupted(detail: String) = appString(R.string.tls_messages_connection_interrupted, detail)

    fun helloTlsMismatch() =
        appString(R.string.tls_messages_encryption_flag_server_hello_doesnt)

    fun helloFingerprintMismatch() =
        appString(R.string.tls_messages_certificate_fingerprint_server_hello_doesnt)
}

sealed class TransportFailure {
    abstract val message: String

    data class Tofu(
        val fingerprint: String,
        override val message: String = TlsMessages.tofu(),
    ) : TransportFailure()

    data class Mismatch(
        val pinned: String,
        val presented: String,
        override val message: String = TlsMessages.mismatch(),
    ) : TransportFailure()

    data class Downgrade(override val message: String = TlsMessages.downgrade()) : TransportFailure()

    data class TlsRequired(override val message: String = TlsMessages.tlsRequired()) : TransportFailure()

    data class Certificate(override val message: String) : TransportFailure()

    data class Other(override val message: String) : TransportFailure()
}
