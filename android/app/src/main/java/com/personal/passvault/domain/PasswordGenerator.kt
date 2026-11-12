package com.personal.passvault.domain

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow

/** Uses SecureRandom only — never kotlin.random.Random. */
object PasswordGenerator {
    private val random = SecureRandom()

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
    private const val LOOK_ALIKES = "0O1lI|"

    data class RandomOptions(
        val length: Int = 20,
        val upper: Boolean = true,
        val lower: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val avoidLookAlikes: Boolean = true,
    )

    data class PassphraseOptions(
        val words: Int = 6,
        val separator: String = "-",
        val capitalize: Boolean = true,
        val addNumber: Boolean = true,
    )

    fun random(o: RandomOptions): String {
        val sets = buildList {
            if (o.upper) add(UPPER)
            if (o.lower) add(LOWER)
            if (o.digits) add(DIGITS)
            if (o.symbols) add(SYMBOLS)
        }.map { set -> if (o.avoidLookAlikes) set.filterNot { it in LOOK_ALIKES } else set }
            .ifEmpty { listOf(LOWER) }
        val all = sets.joinToString("")
        val length = o.length.coerceAtLeast(sets.size)
        // Guarantee one char from each chosen set, then fill and shuffle
        val chars = sets.map { it[random.nextInt(it.length)] }.toMutableList()
        repeat(length - chars.size) { chars += all[random.nextInt(all.length)] }
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return chars.joinToString("")
    }

    fun passphrase(o: PassphraseOptions): String {
        val words = List(o.words.coerceIn(3, 12)) {
            val w = WordList.words[random.nextInt(WordList.words.size)]
            if (o.capitalize) w.replaceFirstChar { it.uppercaseChar() } else w
        }.toMutableList()
        if (o.addNumber) {
            val i = random.nextInt(words.size)
            words[i] = words[i] + random.nextInt(10)
        }
        return words.joinToString(o.separator)
    }

    fun randomEntropyBits(o: RandomOptions): Double {
        var pool = 0
        if (o.upper) pool += 26
        if (o.lower) pool += 26
        if (o.digits) pool += 10
        if (o.symbols) pool += SYMBOLS.length
        if (pool == 0) pool = 26
        return o.length * log2(pool.toDouble())
    }

    fun passphraseEntropyBits(o: PassphraseOptions): Double =
        o.words * log2(WordList.words.size.toDouble()) + (if (o.addNumber) log2(10.0 * o.words) else 0.0)
}

/** Rough strength estimate for passwords the user types in. */
object PasswordStrength {
    enum class Level(val label: String) { VERY_WEAK("Very weak"), WEAK("Weak"), FAIR("Fair"), STRONG("Strong"), VERY_STRONG("Very strong") }

    private val common = setOf(
        "password", "123456", "12345678", "qwerty", "abc123", "111111", "iloveyou", "admin",
        "welcome", "monkey", "dragon", "letmein", "football", "india", "sunshine", "princess",
    )

    fun bits(pw: String): Double {
        if (pw.isEmpty()) return 0.0
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 32
        var bits = pw.length * ln(pool.toDouble().coerceAtLeast(2.0)) / ln(2.0)
        // Penalties: repeats, sequences, common words
        val distinct = pw.toSet().size
        if (distinct < pw.length / 2) bits *= 0.6
        if (common.any { pw.lowercase().contains(it) }) bits *= 0.4
        if (Regex("(0123|1234|2345|3456|4567|5678|6789|abcd|qwer|asdf)").containsMatchIn(pw.lowercase())) bits *= 0.7
        return bits
    }

    fun level(pw: String): Level = levelForBits(bits(pw))

    fun levelForBits(bits: Double): Level = when {
        bits < 28 -> Level.VERY_WEAK
        bits < 40 -> Level.WEAK
        bits < 60 -> Level.FAIR
        bits < 80 -> Level.STRONG
        else -> Level.VERY_STRONG
    }

    /** 0f..1f for the animated meter. */
    fun fraction(bits: Double): Float = (bits / 100.0).coerceIn(0.0, 1.0).toFloat()

    /** Human "time to crack" at 10 billion guesses/sec (offline attack on a fast hash). */
    fun crackTime(bits: Double): String {
        val seconds = 2.0.pow(bits) / 2 / 1e10
        return when {
            seconds < 1 -> "instantly"
            seconds < 3600 -> "minutes"
            seconds < 86_400 -> "hours"
            seconds < 31_536_000 -> "days to months"
            seconds < 3.15e9 -> "years"
            seconds < 3.15e12 -> "centuries"
            else -> "longer than the universe has existed"
        }
    }
}
