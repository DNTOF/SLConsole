package com.dntof.slconsole.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * 启动时核对安装包签名。
 * 只要有一张签名证书的 SHA-256 与官方证书相同,就认为是官方版本。
 */
object SignatureCheck {
    /** 官方发布证书的 SHA-256(大写十六进制,不带冒号)。 */
    const val OFFICIAL_CERT_SHA256 =
        "3D200AA5369B75A07356A1858C07C9D8D50556254A5DF07A3EC9C8E0AAA75036"

    const val RELEASES_URL = "https://github.com/DNTOF/SLConsole/releases"

    @Volatile
    private var cached: Boolean? = null

    /** 本次进程里用户已经关掉过提示;冷启动后重新提示。 */
    @Volatile
    var noticeDismissed: Boolean = false

    fun isOfficial(context: Context): Boolean {
        cached?.let { return it }
        val result = runCatching {
            readSignatures(context).any { sha256Hex(it.toByteArray()) == OFFICIAL_CERT_SHA256 }
        }.getOrDefault(false)
        cached = result
        return result
    }

    @Suppress("DEPRECATION")
    private fun readSignatures(context: Context): List<Signature> {
        val pm = context.packageManager
        val name = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return emptyList()
            val certs = if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
            certs?.toList().orEmpty()
        } else {
            pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}
