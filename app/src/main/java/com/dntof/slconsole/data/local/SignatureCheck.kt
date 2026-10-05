// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.dntof.slconsole.ui.components.badgeSeed
import com.dntof.slconsole.util.formatTable
import java.io.ByteArrayInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory

/**
 * 核对安装包签名。
 *
 * 用两条独立的路径取证书：PackageManager，以及直接解析 APK 文件里的 v2/v3 签名块。
 * 两边的 SHA-256 必须一致，并且等于官方证书，才算官方版本。
 * 结果不是一个布尔值：[Report.mismatch] 是差异位数，[Report.key] 是两条路径一致时的证书指纹，
 * 「正版授权」徽标的配色就是用它解出来的。
 *
 * 开源代码里的校验只能提高修改门槛，无法做到改不掉。
 */
object SignatureCheck {
    const val RELEASES_URL = "https://github.com/DNTOF/SLConsole/releases"

    // 官方证书指纹拆成三段、分别异或后放在不同文件里，见 [officialFingerprint]。
    private val head = intArrayOf(0x9A, 0x87, 0xAD, 0x02, 0x91, 0x3C, 0xD2, 0x07, 0xD4, 0xF1, 0x06)

    class Report(
        /** PackageManager 给出的证书 SHA-256。 */
        val packageManagerHash: ByteArray?,
        /** 从 APK 签名块解析出的证书 SHA-256。 */
        val apkBlockHash: ByteArray?,
        val official: ByteArray,
    ) {
        /** 两条路径一致时为证书指纹，否则全 0。 */
        val key: ByteArray =
            if (packageManagerHash != null && apkBlockHash != null && packageManagerHash.contentEquals(apkBlockHash)) {
                packageManagerHash.copyOf()
            } else {
                ByteArray(32)
            }

        /** 与官方证书、以及两条路径之间相差的位数。0 才是官方版本。 */
        val mismatch: Int =
            SignatureCheck.bitDiff(packageManagerHash, official) +
                SignatureCheck.bitDiff(apkBlockHash, official) +
                SignatureCheck.bitDiff(apkBlockHash, key)
    }

    @Volatile
    private var cached: Report? = null

    /** 本次进程里用户已经关掉过提示;冷启动后重新提示。 */
    @Volatile
    var noticeDismissed: Boolean = false

    fun report(context: Context): Report {
        cached?.let { return it }
        val pm = runCatching { readPackageManagerCert(context)?.let(::sha256) }.getOrNull()
        val apk = runCatching { ApkSigningBlock.firstCertificate(context.applicationInfo.sourceDir)?.let(::sha256) }.getOrNull()
        return Report(pm, apk, officialFingerprint()).also { cached = it }
    }

    /** 官方证书 SHA-256，三段分别在这里、徽标和格式化工具里。 */
    fun officialFingerprint(): ByteArray {
        val out = ByteArray(32)
        head.forEachIndexed { i, v -> out[i] = (v xor 0xA7).toByte() }
        badgeSeed.forEachIndexed { i, v -> out[11 + i] = (v xor ((i * 29 + 0x31) and 0xFF)).toByte() }
        formatTable.forEachIndexed { i, v -> out[22 + i] = (v xor 0x5C).toByte() }
        return out
    }

    fun hex(bytes: ByteArray?, separator: String = ":"): String =
        bytes?.joinToString(separator) { "%02X".format(it.toInt() and 0xFF) } ?: "（读取失败）"

    internal fun bitDiff(a: ByteArray?, b: ByteArray): Int {
        if (a == null || a.size != b.size) return 256
        var bits = 0
        for (i in a.indices) bits += Integer.bitCount((a[i].toInt() xor b[i].toInt()) and 0xFF)
        return bits
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    @Suppress("DEPRECATION")
    private fun readPackageManagerCert(context: Context): ByteArray? {
        val pm = context.packageManager
        val name = context.packageName
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }
        // 多签名的包不当作官方版本。
        return signatures.singleOrNull()?.toByteArray()
    }
}

/**
 * 解析 APK Signature Scheme v2/v3 签名块，取第一个签名者的第一张证书（DER）。
 * 只读取证书，不重复做签名校验（安装时系统已经校验过）。
 */
internal object ApkSigningBlock {
    private const val EOCD_MAGIC = 0x06054b50
    private const val V2_ID = 0x7109871a
    private const val V3_ID = 0xf05368c0.toInt()

    fun firstCertificate(path: String): ByteArray? = RandomAccessFile(path, "r").use { file ->
        val length = file.length()
        val scan = minOf(length, 0xFFFFL + 22).toInt()
        val tail = ByteArray(scan)
        file.seek(length - scan)
        file.readFully(tail)
        val tb = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
        var eocd = -1
        for (i in scan - 22 downTo 0) {
            if (tb.getInt(i) == EOCD_MAGIC && i + 22 + (tb.getShort(i + 20).toInt() and 0xFFFF) == scan) {
                eocd = i
                break
            }
        }
        if (eocd < 0) return null
        val cdOffset = tb.getInt(eocd + 16).toLong() and 0xFFFFFFFFL
        if (cdOffset < 32 || cdOffset > length) return null

        val footer = ByteArray(24)
        file.seek(cdOffset - 24)
        file.readFully(footer)
        if (String(footer, 8, 16, Charsets.US_ASCII) != "APK Sig Block 42") return null
        val blockSize = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
        if (blockSize < 24 || blockSize > 32L * 1024 * 1024) return null
        val total = (blockSize + 8).toInt()
        val start = cdOffset - total
        if (start < 0) return null
        val block = ByteArray(total)
        file.seek(start)
        file.readFully(block)

        val bb = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        val pairs = HashMap<Int, ByteBuffer>()
        var pos = 8
        val end = total - 24
        while (pos + 12 <= end) {
            val pairLength = bb.getLong(pos)
            if (pairLength < 4 || pairLength > end - pos - 8) break
            val id = bb.getInt(pos + 8)
            pairs[id] = slice(bb, pos + 12, (pairLength - 4).toInt())
            pos += 8 + pairLength.toInt()
        }
        val scheme = pairs[V3_ID] ?: pairs[V2_ID] ?: return null
        val signer = lengthPrefixed(lengthPrefixed(scheme))
        val signedData = lengthPrefixed(signer)
        lengthPrefixed(signedData) // digests
        val certificates = lengthPrefixed(signedData)
        val der = lengthPrefixed(certificates)
        val bytes = ByteArray(der.remaining()).also { der.get(it) }
        // 交给 java.security 解析一遍，确认是一张有效的 X.509 证书。
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes))
            .encoded
    }

    private fun slice(source: ByteBuffer, offset: Int, size: Int): ByteBuffer {
        val dup = source.duplicate()
        dup.position(offset)
        dup.limit(offset + size)
        return dup.slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun lengthPrefixed(source: ByteBuffer): ByteBuffer {
        require(source.remaining() >= 4) { "签名块被截断" }
        val size = source.int
        require(size >= 0 && size <= source.remaining()) { "签名块长度不对" }
        val result = slice(source, source.position(), size)
        source.position(source.position() + size)
        return result
    }
}
