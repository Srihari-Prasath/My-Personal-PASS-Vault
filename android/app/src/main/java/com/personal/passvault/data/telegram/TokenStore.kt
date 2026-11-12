package com.personal.passvault.data.telegram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bot token, encrypted with a hardware-backed Keystore key that does NOT need a fingerprint —
 * the background backup upload has to run while the vault is locked.
 * (A leaked token only exposes encrypted backup files; it can't open them.)
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.noBackupFilesDir, "telegram_token.bin")
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Volatile
    private var cache: String? = null

    fun hasToken(): Boolean = file.exists()

    fun save(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        file.writeBytes(byteArrayOf(iv.size.toByte()) + iv + ct)
        cache = token
    }

    fun load(): String? {
        cache?.let { return it }
        if (!file.exists()) return null
        return runCatching {
            val bytes = file.readBytes()
            val ivLen = bytes[0].toInt()
            val iv = bytes.copyOfRange(1, 1 + ivLen)
            val ct = bytes.copyOfRange(1 + ivLen, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull()?.also { cache = it }
    }

    fun clear() {
        file.delete()
        cache = null
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private fun key(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "passvault_telegram_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
