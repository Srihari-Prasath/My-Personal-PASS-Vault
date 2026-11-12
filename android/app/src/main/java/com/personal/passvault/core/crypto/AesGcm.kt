package com.personal.passvault.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM with a random 96-bit nonce. Any tampering makes [decrypt] throw (AEADBadTagException). */
object AesGcm {
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    class Sealed(val nonce: ByteArray, val ciphertext: ByteArray)

    /** [nonce] may be supplied when it has to be written into an authenticated header first (backup files). */
    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray? = null,
        nonce: ByteArray = randomBytes(NONCE_BYTES),
    ): Sealed {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return Sealed(nonce, cipher.doFinal(plaintext))
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }
}
