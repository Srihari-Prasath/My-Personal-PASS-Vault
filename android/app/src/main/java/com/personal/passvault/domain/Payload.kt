package com.personal.passvault.domain

import com.google.gson.Gson

/**
 * Type-specific fields, stored as JSON in `entries.payload` (inside the encrypted DB, and inside backups).
 * Logins can carry a [totp] too; 2FA-only entries use only [totp].
 */
data class EntryPayload(
    val totp: TotpConfig? = null,
    val card: CardData? = null,
    val wifi: WifiData? = null,
) {
    fun toJson(): String = if (totp == null && card == null && wifi == null) "" else gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun parse(json: String?): EntryPayload =
            if (json.isNullOrBlank()) EntryPayload()
            else runCatching { gson.fromJson(json, EntryPayload::class.java) }.getOrNull() ?: EntryPayload()
    }
}

data class TotpConfig(
    val secret: String,          // Base32, no spaces
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: String = "SHA1", // SHA1 | SHA256 | SHA512
    val issuer: String = "",
    val account: String = "",
)

data class CardData(
    val holder: String = "",
    val number: String = "",     // digits only
    val expiry: String = "",     // MM/YY
    val cvv: String = "",
    val pin: String = "",
    val bank: String = "",
) {
    val network: CardNetwork get() = CardNetwork.detect(number)
    val last4: String get() = number.takeLast(4)
}

data class WifiData(
    val ssid: String = "",
    val security: String = "WPA", // WPA | WEP | nopass
    val hidden: Boolean = false,
)

enum class CardNetwork(val label: String) {
    VISA("Visa"), MASTERCARD("Mastercard"), AMEX("American Express"), RUPAY("RuPay"),
    DISCOVER("Discover"), MAESTRO("Maestro"), DINERS("Diners Club"), JCB("JCB"), UNKNOWN("Card");

    companion object {
        fun detect(number: String): CardNetwork {
            val n = number.filter { it.isDigit() }
            if (n.isEmpty()) return UNKNOWN
            val p2 = n.take(2).toIntOrNull() ?: 0
            val p4 = n.take(4).toIntOrNull() ?: 0
            val p6 = n.take(6).toIntOrNull() ?: 0
            return when {
                n.startsWith("4") -> VISA
                p2 in 51..55 || p4 in 2221..2720 -> MASTERCARD
                p2 == 34 || p2 == 37 -> AMEX
                p2 == 60 || p2 == 81 || p2 == 82 || n.startsWith("508") || p6 in 652150..653149 -> RUPAY
                n.startsWith("6011") || p2 == 65 || p6 in 622126..622925 -> DISCOVER
                p2 == 36 || p2 == 38 || n.startsWith("30") -> DINERS
                p4 in 3528..3589 -> JCB
                p2 == 50 || p2 in 56..69 -> MAESTRO
                else -> UNKNOWN
            }
        }

        /** Luhn checksum — catches typos in card numbers. */
        fun luhnValid(number: String): Boolean {
            val digits = number.filter { it.isDigit() }
            if (digits.length !in 12..19) return false
            var sum = 0
            digits.reversed().forEachIndexed { i, c ->
                var d = c - '0'
                if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
                sum += d
            }
            return sum % 10 == 0
        }
    }
}

/** "4111111111111111" -> "4111 1111 1111 1111" (Amex: 4-6-5). */
fun formatCardNumber(number: String, masked: Boolean): String {
    val d = number.filter { it.isDigit() }
    val groups = if (CardNetwork.detect(d) == CardNetwork.AMEX) listOf(4, 6, 5) else List((d.length + 3) / 4) { 4 }
    val out = StringBuilder()
    var i = 0
    for (g in groups) {
        if (i >= d.length) break
        val chunk = d.substring(i, minOf(i + g, d.length))
        val isLast = i + g >= d.length
        if (out.isNotEmpty()) out.append(' ')
        out.append(if (masked && !isLast) "•".repeat(chunk.length) else chunk)
        i += g
    }
    return out.toString()
}
