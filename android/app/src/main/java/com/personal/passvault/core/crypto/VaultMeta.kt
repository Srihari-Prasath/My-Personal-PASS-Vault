package com.personal.passvault.core.crypto

/**
 * Stored as files/vault_meta.json. Contains NO plaintext secrets:
 * only salts, KDF parameters and the Vault Key in wrapped (encrypted) form. All byte fields are Base64.
 */
data class VaultMeta(
    val version: Int = 1,
    val kdfMemoryKiB: Int,
    val kdfIterations: Int,
    val kdfParallelism: Int,
    val passwordSalt: String,
    val vkByPasswordNonce: String,
    val vkByPassword: String,
    val vkByBiometricIv: String? = null,
    val vkByBiometric: String? = null,
    /** Separate salt for backup encryption so backups don't depend on this phone. */
    val backupSalt: String,
    val createdAt: Long,
    /**
     * Backup Key = Argon2id(master, backupSalt), stored wrapped by the Vault Key. This lets the app make
     * backups after a fingerprint unlock (when it never sees the master password), while a backup file
     * can still be opened on any phone with just the master password.
     */
    val bkByVkNonce: String? = null,
    val bkByVk: String? = null,
    /**
     * Decoy vault: its own random key, wrapped by Argon2id(decoy password, SAME passwordSalt) — so one key
     * derivation at unlock tries both, and the timing of a real vs decoy unlock is the same.
     * Also wrapped by the real Vault Key, so the owner can re-set the decoy password without losing decoy entries.
     */
    val decoyVkByPkNonce: String? = null,
    val decoyVkByPk: String? = null,
    val decoyVkByVkNonce: String? = null,
    val decoyVkByVk: String? = null,
) {
    val kdfParams: Argon2Kdf.Params
        get() = Argon2Kdf.Params(kdfMemoryKiB, kdfIterations, kdfParallelism)

    val biometricEnabled: Boolean
        get() = vkByBiometric != null && vkByBiometricIv != null
}
