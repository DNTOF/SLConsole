// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 使用 AndroidKeyStore 的 AES-256-GCM 加密盒,用于在本地持久化 VerifyToken / API Key。
 * 密钥不可导出,卸载应用后数据即不可恢复。
 */
object CryptoBox {
    private const val KEY_ALIAS = "slconsole_master"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PREFIX = "enc1:"

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * 加密失败时抛出异常而不是返回空串:静默失败会把"保存成功"变成"凭据丢失"。
     * GCM IV 由 AndroidKeyStore 随机生成(Keystore 默认禁止调用方提供 IV),
     * 密文格式 "enc1:base64(iv):base64(cipherText)"。
     */
    fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv ?: error("Keystore 未返回 GCM IV")
        return PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        if (stored.isBlank()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val parts = stored.removePrefix(PREFIX).split(":")
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("CryptoBox", "decrypt failed", e)
            ""
        }
    }
}
