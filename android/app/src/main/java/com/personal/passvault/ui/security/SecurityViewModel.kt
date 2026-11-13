package com.personal.passvault.ui.security

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.data.security.BreachChecker
import com.personal.passvault.domain.EntryPayload
import com.personal.passvault.domain.EntryType
import com.personal.passvault.domain.IconSpec
import com.personal.passvault.domain.PasswordStrength
import com.personal.passvault.domain.TargetKind
import com.personal.passvault.domain.TimeText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@Immutable
data class Issue(val entryId: String, val title: String, val icon: IconSpec, val detail: String)

@Immutable
data class SecurityReport(
    val score: Int,
    val passwordCount: Int,
    val weak: List<Issue>,
    val reused: List<Issue>,
    val old: List<Issue>,
    val lowCodes: List<Issue>,
    val cards: List<Issue>,
) {
    val issueCount: Int get() = weak.size + reused.size + old.size + lowCodes.size + cards.size
}

data class BreachState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val found: List<Issue>? = null, // null = never run in this session
    val error: String? = null,
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val breaches: BreachChecker,
) : ViewModel() {

    var report by mutableStateOf<SecurityReport?>(null)
        private set
    var breach by mutableStateOf(BreachState())
        private set

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        report = withContext(Dispatchers.Default) { analyse() }
    }

    private suspend fun analyse(): SecurityReport {
        val data = repo.auditData()
        val now = System.currentTimeMillis()
        val targetsBy = data.targets.groupBy { it.entryId }
        fun iconOf(id: String, title: String) = IconSpec(
            title = title,
            packages = targetsBy[id].orEmpty().filter { it.kind == TargetKind.APP.db }.mapNotNull { it.packageName },
            domains = targetsBy[id].orEmpty().filter { it.kind == TargetKind.WEB.db }.mapNotNull { it.domain },
        )

        val withPw = data.entries.filter { EntryType.from(it.type).usesPassword && it.password.isNotEmpty() }

        val weak = withPw.mapNotNull { e ->
            val level = PasswordStrength.level(e.password)
            if (level < PasswordStrength.Level.FAIR) Issue(e.id, e.title, iconOf(e.id, e.title), "${level.label} password") else null
        }

        val reused = withPw.groupBy { it.password }.values.filter { it.size > 1 }.flatMap { group ->
            group.map { e ->
                val others = group.filter { it.id != e.id }.joinToString { it.title }
                Issue(e.id, e.title, iconOf(e.id, e.title), "Same password as $others")
            }
        }

        val old = withPw.filter { now - it.passwordChangedAt > YEAR_MS }.map { e ->
            Issue(e.id, e.title, iconOf(e.id, e.title), "Changed ${TimeText.ago(e.passwordChangedAt)}")
        }

        val codesBy = data.codes.filter { it.setReplacedAt == null }.groupBy { it.entryId }
        val lowCodes = data.entries.filter { EntryType.from(it.type) == EntryType.RECOVERY_CODES }.mapNotNull { e ->
            val codes = codesBy[e.id].orEmpty()
            val left = codes.count { !it.used }
            when {
                codes.isEmpty() -> Issue(e.id, e.title, iconOf(e.id, e.title), "No codes saved")
                left <= 2 -> Issue(e.id, e.title, iconOf(e.id, e.title), "Only $left of ${codes.size} codes left")
                else -> null
            }
        }

        val cards = data.entries.filter { EntryType.from(it.type) == EntryType.CARD }.mapNotNull { e ->
            val card = EntryPayload.parse(e.payload).card ?: return@mapNotNull null
            val months = monthsUntilExpiry(card.expiry) ?: return@mapNotNull null
            when {
                months < 0 -> Issue(e.id, e.title, iconOf(e.id, e.title), "Expired ${card.expiry}")
                months <= 2 -> Issue(e.id, e.title, iconOf(e.id, e.title), "Expires ${card.expiry}")
                else -> null
            }
        }

        val n = withPw.size
        val penalty = if (n == 0) 0.0 else
            (weak.size * 1.0 + reused.distinctBy { it.entryId }.size * 1.0 + old.size * 0.4) / (n * 1.4)
        val score = ((1.0 - penalty) * 100 - lowCodes.size * 3).toInt().coerceIn(0, 100)

        return SecurityReport(score, n, weak, reused, old, lowCodes, cards)
    }

    /** Called only after the user confirmed the explanation dialog. */
    fun runBreachCheck() {
        if (breach.running) return
        viewModelScope.launch {
            val entries = repo.auditData().entries.filter { EntryType.from(it.type).usesPassword && it.password.isNotEmpty() }
            val byPassword = entries.groupBy { it.password }
            breach = BreachState(running = true, total = byPassword.size)
            val session = BreachChecker.Session()
            val found = mutableListOf<Issue>()
            try {
                var done = 0
                for ((pw, group) in byPassword) {
                    val seen = breaches.timesSeen(pw, session)
                    if (seen > 0) {
                        group.forEach { e ->
                            found += Issue(e.id, e.title, IconSpec(e.title), "Seen ${"%,d".format(seen)} times in data breaches — change it")
                        }
                    }
                    done++
                    breach = breach.copy(done = done)
                }
                breach = BreachState(running = false, done = done, total = byPassword.size, found = found)
            } catch (e: Exception) {
                breach = breach.copy(running = false, error = "Couldn't reach the breach service — check your connection")
            }
        }
    }

    private fun monthsUntilExpiry(expiry: String): Int? {
        val m = Regex("^(\\d{2})/(\\d{2})$").find(expiry.trim()) ?: return null
        val month = m.groupValues[1].toInt()
        val year = 2000 + m.groupValues[2].toInt()
        if (month !in 1..12) return null
        val cal = Calendar.getInstance()
        val nowIndex = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH) // MONTH is 0-based
        return (year * 12 + (month - 1)) - nowIndex
    }

    private companion object {
        const val YEAR_MS = 365L * 24 * 60 * 60 * 1000
    }
}
