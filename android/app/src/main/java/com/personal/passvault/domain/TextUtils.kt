package com.personal.passvault.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Domains {
    private val valid = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /** "https://www.Instagram.com/login?x" -> "instagram.com". Returns null if it isn't a domain. */
    fun normalize(input: String): String? {
        var s = input.trim().lowercase(Locale.ROOT)
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfter('@').substringBefore(':')
        s = s.removePrefix("www.").trimEnd('.')
        return s.takeIf { it.matches(valid) }
    }
}

object TimeText {
    private const val DAY = 24L * 60 * 60 * 1000

    fun date(millis: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    fun dateTime(millis: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    fun monthYear(millis: Long): String = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(millis))

    /** "today", "3 days ago", "5 months ago", "2 years ago" */
    fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
        val days = ((now - millis) / DAY).coerceAtLeast(0)
        return when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days < 30 -> "$days days ago"
            days < 365 -> plural(days / 30, "month") + " ago"
            else -> plural(days / 365, "year") + " ago"
        }
    }

    /** "3 days", "8 months", "1 yr 8 mo" */
    fun duration(fromMillis: Long, toMillis: Long): String {
        val days = ((toMillis - fromMillis) / DAY).coerceAtLeast(0)
        return when {
            days < 1 -> "less than a day"
            days < 30 -> plural(days, "day")
            days < 365 -> plural(days / 30, "month")
            else -> {
                val y = days / 365
                val m = (days % 365) / 30
                if (m == 0L) plural(y, "year") else "$y yr $m mo"
            }
        }
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
}
