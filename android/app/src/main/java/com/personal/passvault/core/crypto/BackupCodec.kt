package com.personal.passvault.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException

class InvalidBackupException(message: String) : Exception(message)
class WrongBackupPasswordException : Exception("Wrong master password for this backup")

/**
 * `.pvlt` backup file:
 *
 * ```
 * magic        "PVLT"          4 bytes
 * version      1               1 byte
 * kdf memory   KiB             4 bytes (big-endian)
 * kdf passes                   4 bytes
 * kdf lanes                    1 byte
 * salt                         16 bytes
 * nonce                        12 bytes
 * ciphertext + GCM tag         rest   = AES-256-GCM( gzip(JSON) ), AAD = all header bytes above
 * ```
 * The header is authenticated (AAD), so changing even the KDF params or salt makes decryption fail.
 * Pure JVM code — unit-tested in BackupCodecTest.
 */
object BackupCodec {
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    private const val VERSION: Byte = 1
    private const val HEADER_SIZE = 4 + 1 + 4 + 4 + 1 + 16 + AesGcm.NONCE_BYTES
    const val FILE_EXTENSION = "pvlt"

    class Header(val params: Argon2Kdf.Params, val salt: ByteArray, val nonce: ByteArray, val raw: ByteArray)

    fun encrypt(json: ByteArray, key: BackupKey): ByteArray {
        val nonce = AesGcm.randomBytes(AesGcm.NONCE_BYTES)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC)
            .put(VERSION)
            .putInt(key.params.memoryKiB)
            .putInt(key.params.iterations)
            .put(key.params.parallelism.toByte())
            .put(key.salt)
            .put(nonce)
            .array()
        val sealed = AesGcm.encrypt(key.key, gzip(json), aad = header, nonce = nonce)
        return header + sealed.ciphertext
    }

    fun readHeader(file: ByteArray): Header {
        if (file.size < HEADER_SIZE + 16) throw InvalidBackupException("File is too small to be a PassVault backup")
        val buf = ByteBuffer.wrap(file)
        val magic = ByteArray(4).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw InvalidBackupException("This isn't a PassVault backup file")
        val version = buf.get()
        if (version != VERSION) throw InvalidBackupException("Backup format v$version needs a newer PassVault")
        val memory = buf.getInt()
        val passes = buf.getInt()
        val lanes = buf.get().toInt() and 0xFF
        // Refuse absurd parameters (a crafted file could otherwise make the phone run out of memory)
        if (memory !in 8_192..1_048_576 || passes !in 1..20 || lanes !in 1..16) {
            throw InvalidBackupException("Backup header looks corrupted")
        }
        val salt = ByteArray(16).also { buf.get(it) }
        val nonce = ByteArray(AesGcm.NONCE_BYTES).also { buf.get(it) }
        return Header(Argon2Kdf.Params(memory, passes, lanes), salt, nonce, file.copyOfRange(0, HEADER_SIZE))
    }

    /** Slow (Argon2id): run off the main thread. */
    fun decryptWithPassword(file: ByteArray, master: CharArray): ByteArray {
        val header = readHeader(file)
        val key = Argon2Kdf.derive(master, header.salt, header.params)
        try {
            return decrypt(file, header, key)
        } finally {
            key.fill(0)
        }
    }

    fun decryptWithKey(file: ByteArray, key: ByteArray): ByteArray = decrypt(file, readHeader(file), key)

    private fun decrypt(file: ByteArray, header: Header, key: ByteArray): ByteArray {
        val ciphertext = file.copyOfRange(HEADER_SIZE, file.size)
        val compressed = try {
            AesGcm.decrypt(key, header.nonce, ciphertext, aad = header.raw)
        } catch (e: AEADBadTagException) {
            throw WrongBackupPasswordException()
        }
        return gunzip(compressed)
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
