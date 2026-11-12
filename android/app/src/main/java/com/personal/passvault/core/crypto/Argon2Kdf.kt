package com.personal.passvault.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * Argon2id key derivation (master password -> 256-bit key).
 * Parameters are stored next to every salt so they can be raised later without breaking old vaults/backups.
 */
object Argon2Kdf {

    data class Params(
        val memoryKiB: Int = 46_080,   // 45 MiB  (OWASP 2024 recommendation for Argon2id)
        val iterations: Int = 2,
        val parallelism: Int = 1,
    )

    fun derive(password: CharArray, salt: ByteArray, params: Params, length: Int = 32): ByteArray {
        val pwBytes = password.toUtf8Bytes()
        try {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(params.iterations)
                .withMemoryAsKB(params.memoryKiB)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(argon)
            val out = ByteArray(length)
            generator.generateBytes(pwBytes, out)
            return out
        } finally {
            pwBytes.fill(0)
        }
    }

    /** UTF-8 encode without creating an immutable String copy of the password. */
    private fun CharArray.toUtf8Bytes(): ByteArray {
        val buffer: ByteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}
