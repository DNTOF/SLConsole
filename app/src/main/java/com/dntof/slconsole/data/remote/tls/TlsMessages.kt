// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

/** 给界面看的短句。指纹正文由对话框另排,不塞进这一句里。 */
object TlsMessages {
    const val PLAINTEXT_BANNER = "此连接不加密"
    const val PLAINTEXT_DETAIL = "当前插件没有提供 TLS（旧版本或 tls_mode 为 off）。请升级到带 TLS 的 SLDataAPI，之后会自动加密。"

    fun tofu() =
        "第一次加密连接这台服务器。请对照控制台的证书指纹后再信任（sldataapi cert show，或启动时的 TLS 横幅）。"

    fun mismatch() =
        "证书指纹和已固定的不一致。可能是管理员执行了 sldataapi cert regen，也可能有人在中间拦截。请对照服务器控制台。应用不会因此改走明文。"

    fun downgrade() =
        "这台服务器曾经用加密连通过，这次加密握手失败，已拒绝改用明文。如果确认服务器关掉了 TLS，请到服务器编辑页重置加密记录。"

    fun tlsRequired() =
        "服务器要求加密（HTTP 426，tls_required），但 TLS 握手没有成功。请确认 SLDataAPI 已开启 TLS，并核对证书指纹。"

    fun certificate(detail: String) = "证书校验失败，已停止连接，不会改走明文。$detail"

    fun other(detail: String) = "加密连接失败：$detail"

    /** 握手已经完成,之后响应流被掐断。不要把它说成加密失败。 */
    fun interrupted(detail: String) = "连接中断：$detail"

    fun helloTlsMismatch() =
        "服务器 hello 里的加密标记和实际连接不一致，已断开。应用不会按 hello 改走明文。"

    fun helloFingerprintMismatch() =
        "服务器 hello 里的证书指纹和已固定的指纹不一致，已断开。应用不会按 hello 更换指纹或改走明文。"
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
