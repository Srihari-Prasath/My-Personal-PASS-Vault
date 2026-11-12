package com.personal.passvault.core.crypto

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/** Key + the salt/params needed to rebuild it from the master password on another phone. */
class BackupKey(val key: ByteArray, val salt: ByteArray, val params: Argon2Kdf.Params) {
    fun wipe() = key.fill(0)
}

/**
 * Owns the Vault Key (VK) life cycle:
 *  - create: random VK, wrapped by Argon2id(master password); Backup Key derived and wrapped by VK
 *  - unlock with password / fingerprint -> returns a copy of VK (caller must wipe it)
 *  - enable/disable fingerprint, change master password, wipe
 */
@Singleton
class VaultKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystore: KeystoreManager,
    private val gson: Gson,
) {
    private val metaFile = File(context.filesDir, "vault_meta.json")

    @Volatile
    private var cached: VaultMeta? = null

    fun vaultExists(): Boolean = metaFile.exists()

    fun meta(): VaultMeta? {
        cached?.let { return it }
        if (!metaFile.exists()) return null
        return gson.fromJson(metaFile.readText(), VaultMeta::class.java).also { cached = it }
    }

    fun isBiometricEnabled(): Boolean = meta()?.biometricEnabled == true

    /** Creates a brand new vault. Returns the Vault Key (caller hands it to VaultSession). */
    suspend fun createVault(master: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val params = Argon2Kdf.Params()
        val salt = AesGcm.randomBytes(16)
        val backupSalt = AesGcm.randomBytes(16)
        val vk = AesGcm.randomBytes(32)
        val pk = Argon2Kdf.derive(master, salt, params)
        val bk = Argon2Kdf.derive(master, backupSalt, params)
        try {
            val wrapped = AesGcm.encrypt(pk, vk, VK_AAD)
            val wrappedBk = AesGcm.encrypt(vk, bk, BK_AAD)
            save(
                VaultMeta(
                    kdfMemoryKiB = params.memoryKiB,
                    kdfIterations = params.iterations,
                    kdfParallelism = params.parallelism,
                    passwordSalt = salt.b64(),
                    vkByPasswordNonce = wrapped.nonce.b64(),
                    vkByPassword = wrapped.ciphertext.b64(),
                    backupSalt = backupSalt.b64(),
                    createdAt = System.currentTimeMillis(),
                    bkByVkNonce = wrappedBk.nonce.b64(),
                    bkByVk = wrappedBk.ciphertext.b64(),
                )
            )
            vk
        } finally {
            pk.fill(0)
            bk.fill(0)
        }
    }

    /** [decoy] = the password opened the decoy vault, not the real one. Caller must wipe [vk]. */
    class UnlockResult(val vk: ByteArray, val decoy: Boolean)

    /**
     * Unlock screen: one Argon2id derivation, then try the real vault key and (if set) the decoy key.
     * Returns null if the password opens neither.
     */
    suspend fun unlockAny(master: CharArray): UnlockResult? = withContext(Dispatchers.Default) {
        val meta = meta() ?: return@withContext null
        val pk = Argon2Kdf.derive(master, meta.passwordSalt.unb64(), meta.kdfParams)
        try {
            val real = tryDecrypt(pk, meta.vkByPasswordNonce, meta.vkByPassword, VK_AAD)
            if (real != null) {
                // Vaults created before backups existed: derive + store the Backup Key now (needs the password once)
                if (meta.bkByVk == null) {
                    val bk = Argon2Kdf.derive(master, meta.backupSalt.unb64(), meta.kdfParams)
                    val wrappedBk = AesGcm.encrypt(real, bk, BK_AAD)
                    bk.fill(0)
                    save(meta.copy(bkByVkNonce = wrappedBk.nonce.b64(), bkByVk = wrappedBk.ciphertext.b64()))
                }
                return@withContext UnlockResult(real, decoy = false)
            }
            val decoy = if (meta.decoyVkByPk != null && meta.decoyVkByPkNonce != null) {
                tryDecrypt(pk, meta.decoyVkByPkNonce, meta.decoyVkByPk, DECOY_AAD)
            } else null
            decoy?.let { UnlockResult(it, decoy = true) }
        } finally {
            pk.fill(0)
        }
    }

    /** The REAL Vault Key only (settings checks like wipe / change password). Null if wrong or decoy. */
    suspend fun unlockWithPassword(master: CharArray): ByteArray? {
        val r = unlockAny(master) ?: return null
        if (r.decoy) { r.vk.fill(0); return null }
        return r.vk
    }

    private fun tryDecrypt(key: ByteArray, nonce: String, ct: String, aad: ByteArray): ByteArray? = try {
        AesGcm.decrypt(key, nonce.unb64(), ct.unb64(), aad)
    } catch (e: AEADBadTagException) {
        null
    }

    // ---------- Decoy vault ----------

    enum class DecoyStatus { OFF, ON, NEEDS_PASSWORD }

    fun decoyStatus(): DecoyStatus {
        val m = meta() ?: return DecoyStatus.OFF
        return when {
            m.decoyVkByPk != null -> DecoyStatus.ON
            m.decoyVkByVk != null -> DecoyStatus.NEEDS_PASSWORD // master password was changed since
            else -> DecoyStatus.OFF
        }
    }

    /**
     * Sets (or re-sets) the decoy password while the REAL vault is open. Keeps existing decoy entries.
     * Returns false if the decoy password equals the master password.
     */
    suspend fun setDecoyPassword(realVk: ByteArray, decoyPassword: CharArray): Boolean = withContext(Dispatchers.Default) {
        val meta = meta() ?: return@withContext false
        val pk = Argon2Kdf.derive(decoyPassword, meta.passwordSalt.unb64(), meta.kdfParams)
        try {
            tryDecrypt(pk, meta.vkByPasswordNonce, meta.vkByPassword, VK_AAD)?.let { it.fill(0); return@withContext false }
            val decoyVk = meta.decoyVkByVk?.let { tryDecrypt(realVk, meta.decoyVkByVkNonce!!, it, DECOY_BY_VK_AAD) }
                ?: AesGcm.randomBytes(32)
            val byPk = AesGcm.encrypt(pk, decoyVk, DECOY_AAD)
            val byVk = AesGcm.encrypt(realVk, decoyVk, DECOY_BY_VK_AAD)
            decoyVk.fill(0)
            save(
                meta.copy(
                    decoyVkByPkNonce = byPk.nonce.b64(), decoyVkByPk = byPk.ciphertext.b64(),
                    decoyVkByVkNonce = byVk.nonce.b64(), decoyVkByVk = byVk.ciphertext.b64(),
                )
            )
            true
        } finally {
            pk.fill(0)
        }
    }

    /** Used when someone changes "the master password" from inside the decoy vault. */
    suspend fun changeDecoyPassword(old: CharArray, new: CharArray): Boolean = withContext(Dispatchers.Default) {
        val meta = meta() ?: return@withContext false
        val nonce = meta.decoyVkByPkNonce ?: return@withContext false
        val wrapped = meta.decoyVkByPk ?: return@withContext false
        val oldPk = Argon2Kdf.derive(old, meta.passwordSalt.unb64(), meta.kdfParams)
        val decoyVk = tryDecrypt(oldPk, nonce, wrapped, DECOY_AAD).also { oldPk.fill(0) } ?: return@withContext false
        val newPk = Argon2Kdf.derive(new, meta.passwordSalt.unb64(), meta.kdfParams)
        try {
            // Never let the decoy password become the real one
            tryDecrypt(newPk, meta.vkByPasswordNonce, meta.vkByPassword, VK_AAD)?.let { it.fill(0); return@withContext false }
            val byPk = AesGcm.encrypt(newPk, decoyVk, DECOY_AAD)
            save(meta.copy(decoyVkByPkNonce = byPk.nonce.b64(), decoyVkByPk = byPk.ciphertext.b64()))
            true
        } finally {
            newPk.fill(0)
            decoyVk.fill(0)
        }
    }

    /** Turns the decoy off (the caller also deletes decoy.db). */
    fun removeDecoy() {
        val meta = meta() ?: return
        save(meta.copy(decoyVkByPkNonce = null, decoyVkByPk = null, decoyVkByVkNonce = null, decoyVkByVk = null))
    }

    /** The Backup Key, unwrapped with the live Vault Key. Null if not set up yet (unlock with password once). */
    fun backupKey(vk: ByteArray): BackupKey? {
        val meta = meta() ?: return null
        val nonce = meta.bkByVkNonce ?: return null
        val wrapped = meta.bkByVk ?: return null
        return try {
            val bk = AesGcm.decrypt(vk, nonce.unb64(), wrapped.unb64(), BK_AAD)
            BackupKey(bk, meta.backupSalt.unb64(), meta.kdfParams)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Fingerprint ----------

    /** Step 1 of enabling: a cipher that must be authorised through BiometricPrompt. */
    fun biometricEnrollCipher(): Cipher = keystore.newEncryptCipher()

    /** Step 2 of enabling: wrap the VK with the now-authorised cipher. */
    fun enableBiometric(authorisedCipher: Cipher, vk: ByteArray) {
        val meta = meta() ?: return
        val wrapped = authorisedCipher.doFinal(vk)
        save(meta.copy(vkByBiometricIv = authorisedCipher.iv.b64(), vkByBiometric = wrapped.b64()))
    }

    /**
     * Cipher for fingerprint unlock, or null if fingerprint isn't set up.
     * If the user enrolled a new fingerprint the key is invalidated -> we disable fingerprint and return null.
     */
    fun biometricUnlockCipher(): Cipher? {
        val meta = meta() ?: return null
        val iv = meta.vkByBiometricIv ?: return null
        return try {
            keystore.decryptCipher(iv.unb64())
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            disableBiometric()
            null
        }
    }

    fun unlockWithBiometric(authorisedCipher: Cipher): ByteArray? {
        val wrapped = meta()?.vkByBiometric ?: return null
        return try {
            authorisedCipher.doFinal(wrapped.unb64())
        } catch (e: Exception) {
            null
        }
    }

    fun disableBiometric() {
        keystore.deleteKey()
        val meta = meta() ?: return
        save(meta.copy(vkByBiometricIv = null, vkByBiometric = null))
    }

    // ---------- Master password ----------

    /**
     * Re-wraps the same VK under a new password (data is not re-encrypted) and creates a new Backup Key,
     * so backups made from now on need the NEW password. Returns false if [old] is wrong.
     */
    suspend fun changeMasterPassword(old: CharArray, new: CharArray): Boolean = withContext(Dispatchers.Default) {
        val vk = unlockWithPassword(old) ?: return@withContext false
        val meta = meta()!!
        val params = Argon2Kdf.Params()
        val salt = AesGcm.randomBytes(16)
        val backupSalt = AesGcm.randomBytes(16)
        val pk = Argon2Kdf.derive(new, salt, params)
        val bk = Argon2Kdf.derive(new, backupSalt, params)
        try {
            val wrapped = AesGcm.encrypt(pk, vk, VK_AAD)
            val wrappedBk = AesGcm.encrypt(vk, bk, BK_AAD)
            save(
                meta.copy(
                    kdfMemoryKiB = params.memoryKiB,
                    kdfIterations = params.iterations,
                    kdfParallelism = params.parallelism,
                    passwordSalt = salt.b64(),
                    vkByPasswordNonce = wrapped.nonce.b64(),
                    vkByPassword = wrapped.ciphertext.b64(),
                    backupSalt = backupSalt.b64(),
                    bkByVkNonce = wrappedBk.nonce.b64(),
                    bkByVk = wrappedBk.ciphertext.b64(),
                    // New salt -> the decoy password no longer derives the right key. Decoy entries are kept
                    // (decoyVkByVk) and Settings asks the owner to set the decoy password again.
                    decoyVkByPkNonce = null,
                    decoyVkByPk = null,
                )
            )
            true
        } finally {
            pk.fill(0)
            bk.fill(0)
            vk.fill(0)
        }
    }

    /** Deletes the key material. Together with deleting vault.db this makes the data unrecoverable. */
    fun wipe() {
        keystore.deleteKey()
        metaFile.delete()
        cached = null
    }

    private fun save(meta: VaultMeta) {
        // Write to a temp file and rename, so a crash mid-write can't corrupt the only copy of the wrapped key
        val tmp = File(metaFile.parentFile, "vault_meta.json.tmp")
        tmp.writeText(gson.toJson(meta))
        if (!tmp.renameTo(metaFile)) {
            metaFile.writeText(gson.toJson(meta))
            tmp.delete()
        }
        cached = meta
    }

    private companion object {
        val VK_AAD = "passvault-vault-key-v1".toByteArray()
        val BK_AAD = "passvault-backup-key-v1".toByteArray()
        val DECOY_AAD = "passvault-decoy-key-v1".toByteArray()
        val DECOY_BY_VK_AAD = "passvault-decoy-by-vault-key-v1".toByteArray()
    }
}

internal fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
internal fun String.unb64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
