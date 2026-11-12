package com.personal.passvault

import com.personal.passvault.core.crypto.AesGcm
import com.personal.passvault.core.crypto.Argon2Kdf
import com.personal.passvault.domain.Domains
import com.personal.passvault.domain.PasswordGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

/** JVM unit tests for the parts that must never be wrong. Run: ./gradlew :app:testDebugUnitTest */
class CryptoTest {

    // Small params so the tests run fast; production uses Argon2Kdf.Params()
    private val fast = Argon2Kdf.Params(memoryKiB = 1024, iterations = 1, parallelism = 1)

    @Test
    fun argon2_isDeterministic_andSaltSensitive() {
        val salt = ByteArray(16) { 1 }
        val a = Argon2Kdf.derive("correct horse".toCharArray(), salt, fast)
        val b = Argon2Kdf.derive("correct horse".toCharArray(), salt, fast)
        val c = Argon2Kdf.derive("correct horse".toCharArray(), ByteArray(16) { 2 }, fast)
        val d = Argon2Kdf.derive("correct horsE".toCharArray(), salt, fast)
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(c))
        assertFalse(a.contentEquals(d))
    }

    @Test
    fun aesGcm_roundTrip() {
        val key = AesGcm.randomBytes(32)
        val plain = "my secret password 🔐".toByteArray()
        val sealed = AesGcm.encrypt(key, plain, "aad".toByteArray())
        val back = AesGcm.decrypt(key, sealed.nonce, sealed.ciphertext, "aad".toByteArray())
        assertArrayEquals(plain, back)
    }

    @Test
    fun aesGcm_noncesAreUnique() {
        val key = AesGcm.randomBytes(32)
        val a = AesGcm.encrypt(key, byteArrayOf(1, 2, 3))
        val b = AesGcm.encrypt(key, byteArrayOf(1, 2, 3))
        assertFalse(a.nonce.contentEquals(b.nonce))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
    }

    @Test
    fun aesGcm_wrongKey_fails() {
        val sealed = AesGcm.encrypt(AesGcm.randomBytes(32), "x".toByteArray())
        expectTamperFailure { AesGcm.decrypt(AesGcm.randomBytes(32), sealed.nonce, sealed.ciphertext) }
    }

    @Test
    fun aesGcm_flippedByte_fails() {
        val key = AesGcm.randomBytes(32)
        val sealed = AesGcm.encrypt(key, "hello vault".toByteArray())
        for (i in sealed.ciphertext.indices) {
            val tampered = sealed.ciphertext.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            expectTamperFailure { AesGcm.decrypt(key, sealed.nonce, tampered) }
        }
    }

    @Test
    fun aesGcm_wrongAad_fails() {
        val key = AesGcm.randomBytes(32)
        val sealed = AesGcm.encrypt(key, "x".toByteArray(), "v1".toByteArray())
        expectTamperFailure { AesGcm.decrypt(key, sealed.nonce, sealed.ciphertext, "v2".toByteArray()) }
    }

    @Test
    fun wrongMasterPassword_cannotUnwrapVaultKey() {
        val salt = AesGcm.randomBytes(16)
        val vk = AesGcm.randomBytes(32)
        val pk = Argon2Kdf.derive("right password".toCharArray(), salt, fast)
        val wrapped = AesGcm.encrypt(pk, vk)
        val wrongPk = Argon2Kdf.derive("wrong password".toCharArray(), salt, fast)
        expectTamperFailure { AesGcm.decrypt(wrongPk, wrapped.nonce, wrapped.ciphertext) }
        assertArrayEquals(vk, AesGcm.decrypt(pk, wrapped.nonce, wrapped.ciphertext))
    }

    @Test
    fun generator_respectsOptions() {
        repeat(200) {
            val pw = PasswordGenerator.random(PasswordGenerator.RandomOptions(length = 24))
            assertEquals(24, pw.length)
            assertTrue(pw.any { it.isUpperCase() })
            assertTrue(pw.any { it.isLowerCase() })
            assertTrue(pw.any { it.isDigit() })
            assertTrue(pw.any { !it.isLetterOrDigit() })
            assertTrue(pw.none { it in "0O1lI|" })
        }
        val digitsOnly = PasswordGenerator.random(
            PasswordGenerator.RandomOptions(length = 10, upper = false, lower = false, symbols = false, avoidLookAlikes = false)
        )
        assertTrue(digitsOnly.all { it.isDigit() })
        assertNotEquals(
            PasswordGenerator.random(PasswordGenerator.RandomOptions()),
            PasswordGenerator.random(PasswordGenerator.RandomOptions()),
        )
    }

    @Test
    fun domains_normalize() {
        assertEquals("instagram.com", Domains.normalize("https://www.Instagram.com/accounts/login?x=1"))
        assertEquals("mail.google.com", Domains.normalize("mail.google.com"))
        assertEquals("onlinesbi.sbi", Domains.normalize("http://onlinesbi.sbi:443/"))
        assertNull(Domains.normalize("not a domain"))
        assertNull(Domains.normalize("localhost"))
    }

    private fun expectTamperFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected decryption to fail")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }
}
