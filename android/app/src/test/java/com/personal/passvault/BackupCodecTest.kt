package com.personal.passvault

import com.personal.passvault.core.crypto.AesGcm
import com.personal.passvault.core.crypto.Argon2Kdf
import com.personal.passvault.core.crypto.BackupCodec
import com.personal.passvault.core.crypto.BackupKey
import com.personal.passvault.core.crypto.InvalidBackupException
import com.personal.passvault.core.crypto.WrongBackupPasswordException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The backup file must open with ONLY the master password, and must reject anything tampered. */
class BackupCodecTest {

    // Smallest params the codec accepts, so the tests stay fast
    private val params = Argon2Kdf.Params(memoryKiB = 8_192, iterations = 1, parallelism = 1)
    private val json = """{"format":1,"entries":[{"title":"Instagram","password":"hunter2"}]}""".toByteArray()

    private fun keyFor(password: String, salt: ByteArray = AesGcm.randomBytes(16)) =
        BackupKey(Argon2Kdf.derive(password.toCharArray(), salt, params), salt, params)

    @Test
    fun opensWithMasterPasswordAlone() {
        val file = BackupCodec.encrypt(json, keyFor("my master password"))
        assertArrayEquals(json, BackupCodec.decryptWithPassword(file, "my master password".toCharArray()))
    }

    @Test
    fun headerCarriesKdfParams() {
        val file = BackupCodec.encrypt(json, keyFor("pw"))
        val header = BackupCodec.readHeader(file)
        assertEquals(params, header.params)
    }

    @Test
    fun contentIsNotReadableInTheFile() {
        val file = BackupCodec.encrypt(json, keyFor("pw"))
        assertFalse(String(file, Charsets.ISO_8859_1).contains("hunter2"))
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Instagram"))
    }

    @Test(expected = WrongBackupPasswordException::class)
    fun wrongPasswordIsRejected() {
        val file = BackupCodec.encrypt(json, keyFor("right"))
        BackupCodec.decryptWithPassword(file, "wrong".toCharArray())
    }

    @Test(expected = WrongBackupPasswordException::class)
    fun tamperedCiphertextIsRejected() {
        val key = keyFor("pw")
        val file = BackupCodec.encrypt(json, key)
        file[file.size - 20] = (file[file.size - 20].toInt() xor 1).toByte()
        BackupCodec.decryptWithKey(file, key.key)
    }

    @Test(expected = WrongBackupPasswordException::class)
    fun tamperedHeaderIsRejected() {
        val key = keyFor("pw")
        val file = BackupCodec.encrypt(json, key)
        file[20] = (file[20].toInt() xor 1).toByte() // inside the salt
        BackupCodec.decryptWithKey(file, key.key)
    }

    @Test(expected = InvalidBackupException::class)
    fun notABackupFile() {
        BackupCodec.readHeader("hello this is definitely not a vault backup file at all".toByteArray())
    }

    @Test(expected = InvalidBackupException::class)
    fun absurdKdfParamsAreRefused() {
        val file = BackupCodec.encrypt(json, keyFor("pw"))
        // memory field (bytes 5..8) -> 0x7FFFFFFF KiB: would exhaust the phone's memory
        file[5] = 0x7F; file[6] = 0xFF.toByte(); file[7] = 0xFF.toByte(); file[8] = 0xFF.toByte()
        BackupCodec.readHeader(file)
    }
}
