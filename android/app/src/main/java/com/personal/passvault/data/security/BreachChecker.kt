package com.personal.passvault.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Have I Been Pwned" Pwned Passwords, k-anonymity model:
 * only the FIRST 5 hex characters of the password's SHA-1 are sent; the service returns ~800 matching
 * suffixes (+ random padding) and the comparison happens on the phone. The password never leaves the device.
 */
@Singleton
class BreachChecker @Inject constructor(private val http: OkHttpClient) {

    /** Per check-run cache, so 5 passwords sharing a prefix cost one request. */
    class Session {
        internal val ranges = HashMap<String, Map<String, Int>>()
    }

    /** How many times this password appears in known breaches (0 = not found). */
    suspend fun timesSeen(password: String, session: Session): Int = withContext(Dispatchers.IO) {
        val hash = sha1Hex(password)
        val prefix = hash.substring(0, 5)
        val suffix = hash.substring(5)
        val range = session.ranges.getOrPut(prefix) { fetchRange(prefix) }
        range[suffix] ?: 0
    }

    private fun fetchRange(prefix: String): Map<String, Int> {
        val request = Request.Builder()
            .url("https://api.pwnedpasswords.com/range/$prefix")
            .header("Add-Padding", "true") // hides how many real matches there are
            .header("User-Agent", "PassVault-Android")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Breach service returned HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            return body.lineSequence().mapNotNull { line ->
                val parts = line.trim().split(':')
                if (parts.size != 2) null else parts[0].uppercase() to (parts[1].toIntOrNull() ?: 0)
            }.filter { it.second > 0 }.toMap()
        }
    }

    companion object {
        fun sha1Hex(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02X".format(it) }
    }
}
