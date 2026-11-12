package com.personal.passvault.domain

import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 time-based one-time passwords (what Google Authenticator & co. show). Pure JVM — unit tested. */
object Totp {

    fun code(config: TotpConfig, timeMillis: Long = System.currentTimeMillis()): String {
        val key = Base32.decode(config.secret)
        val counter = timeMillis / 1000 / config.period
        val algorithm = when (config.algorithm.uppercase()) {
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }
        val mac = Mac.getInstance(algorithm).apply { init(SecretKeySpec(key, algorithm)) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var mod = 1
        repeat(config.digits) { mod *= 10 }
        return (binary % mod).toString().padStart(config.digits, '0')
    }

    /** Seconds until the code changes. */
    fun secondsLeft(config: TotpConfig, timeMillis: Long = System.currentTimeMillis()): Int =
        config.period - ((timeMillis / 1000) % config.period).toInt()

    /** "123456" -> "123 456", "12345678" -> "1234 5678" */
    fun pretty(code: String): String = when (code.length) {
        6 -> code.substring(0, 3) + " " + code.substring(3)
        8 -> code.substring(0, 4) + " " + code.substring(4)
        else -> code
    }

    /** Valid Base32 secret (spaces/dashes allowed, any case). */
    fun isValidSecret(secret: String): Boolean {
        val clean = Base32.clean(secret)
        return clean.length >= 16 && runCatching { Base32.decode(clean) }.isSuccess
    }

    /**
     * Accepts either an `otpauth://totp/Issuer:account?secret=…&issuer=…` link (what QR codes contain)
     * or a plain Base32 secret. Returns null if neither.
     */
    fun parse(input: String): TotpConfig? {
        val text = input.trim()
        if (!text.startsWith("otpauth://", ignoreCase = true)) {
            return if (isValidSecret(text)) TotpConfig(secret = Base32.clean(text)) else null
        }
        val afterScheme = text.substring("otpauth://".length)
        val type = afterScheme.substringBefore('/').lowercase()
        if (type != "totp") return null // HOTP (counter-based) isn't supported
        val label = decode(afterScheme.substringAfter('/').substringBefore('?'))
        val params = afterScheme.substringAfter('?', "").split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            if (k.isEmpty()) null else k.lowercase() to decode(it.substringAfter('='))
        }.toMap()
        val secret = params["secret"]?.let(Base32::clean) ?: return null
        if (!isValidSecret(secret)) return null
        val labelIssuer = if (':' in label) label.substringBefore(':').trim() else ""
        val account = if (':' in label) label.substringAfter(':').trim() else label.trim()
        return TotpConfig(
            secret = secret,
            digits = params["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6,
            period = params["period"]?.toIntOrNull()?.takeIf { it in 10..120 } ?: 30,
            algorithm = params["algorithm"]?.uppercase()?.takeIf { it in setOf("SHA1", "SHA256", "SHA512") } ?: "SHA1",
            issuer = params["issuer"]?.trim()?.ifBlank { null } ?: labelIssuer,
            account = account,
        )
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
}

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun clean(s: String): String = s.uppercase().filter { it != ' ' && it != '-' && it != '=' }

    fun decode(input: String): ByteArray {
        val s = clean(input)
        require(s.isNotEmpty()) { "Empty secret" }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in s) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "Invalid character in secret" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }
}

/** The `WIFI:T:WPA;S:MyNet;P:secret;H:false;;` format phones use for Wi-Fi QR codes. */
object WifiQr {
    data class Parsed(val ssid: String, val password: String, val security: String, val hidden: Boolean)

    fun format(ssid: String, password: String, security: String, hidden: Boolean): String {
        val t = if (security == "nopass" || password.isEmpty()) "nopass" else security
        return buildString {
            append("WIFI:T:").append(t).append(';')
            append("S:").append(escape(ssid)).append(';')
            if (t != "nopass") append("P:").append(escape(password)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }
    }

    fun parse(text: String): Parsed? {
        if (!text.startsWith("WIFI:", ignoreCase = true)) return null
        val fields = mutableMapOf<String, String>()
        var i = 5
        val body = text
        while (i < body.length) {
            val colon = body.indexOf(':', i)
            if (colon < 0) break
            val key = body.substring(i, colon).uppercase()
            val value = StringBuilder()
            var j = colon + 1
            while (j < body.length && body[j] != ';') {
                if (body[j] == '\\' && j + 1 < body.length) { value.append(body[j + 1]); j += 2 } else { value.append(body[j]); j++ }
            }
            if (key.isNotEmpty()) fields[key] = value.toString()
            i = j + 1
            if (i < body.length && body[i] == ';') break
        }
        val ssid = fields["S"] ?: return null
        val type = fields["T"]?.uppercase()?.let { if (it.isBlank() || it == "NOPASS") "nopass" else if (it.startsWith("WEP")) "WEP" else "WPA" } ?: "nopass"
        return Parsed(ssid, fields["P"].orEmpty(), type, fields["H"].equals("true", ignoreCase = true))
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
}
