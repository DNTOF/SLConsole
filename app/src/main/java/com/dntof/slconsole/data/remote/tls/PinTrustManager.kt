// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

/** 还没有指纹。握手只为了拿到证书,然后拒绝,不把请求发出去。 */
class TofuRequiredException(val fingerprint: String) : CertificateException(appString(R.string.pin_trust_manager_certificate_fingerprint_must_confirmed_fir))

/** 出示的证书和已固定的指纹不一致。禁止退回明文。 */
class PinMismatchException(
    val expected: String,
    val presented: String,
) : CertificateException(appString(R.string.common_certificate_fingerprint_mismatch))

/**
 * 只认这一台服务器的指纹:比较叶子证书 DER 的 SHA-256,不校验证书链,也不看主机名。
 * 主机名由调用方在这个客户端上单独跳过;系统信任库保持原样,给 GitHub 更新检查用。
 */
class PinTrustManager(expectedPin: String?) : X509TrustManager {
    val expected: String? = expectedPin?.let { CertFingerprint.normalize(it) }

    @Volatile
    var presented: String? = null

    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        throw CertificateException(appString(R.string.pin_trust_manager_client_certificates_are_not_used))
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException(appString(R.string.pin_trust_manager_server_presented_no_certificate))
        val fingerprint = CertFingerprint.sha256Der(chain[0].encoded)
        presented = fingerprint
        val pin = expected ?: throw TofuRequiredException(fingerprint)
        if (fingerprint != pin) throw PinMismatchException(pin, fingerprint)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
