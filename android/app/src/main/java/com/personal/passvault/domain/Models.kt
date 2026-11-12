package com.personal.passvault.domain

import androidx.compose.runtime.Immutable
import com.personal.passvault.data.db.EntryEntity
import com.personal.passvault.data.db.LinkedTargetEntity
import com.personal.passvault.data.db.PasswordHistoryEntity
import com.personal.passvault.data.db.RecoveryCodeEntity

enum class EntryType(val label: String) {
    LOGIN("Login"),
    RECOVERY_CODES("Recovery codes"),
    NOTE("Secure note"),
    TOTP("2FA codes"),
    CARD("Card"),
    WIFI("Wi-Fi");

    /** Types whose main secret lives in entries.password (so they get history + reuse checks). */
    val usesPassword: Boolean get() = this == LOGIN || this == WIFI

    companion object {
        fun from(name: String?): EntryType = entries.firstOrNull { it.name == name } ?: LOGIN
    }
}

enum class TargetKind(val db: String) {
    APP("app"), WEB("web");

    companion object {
        fun from(db: String): TargetKind = if (db == "app") APP else WEB
    }
}

/** Everything needed to pick an icon. Also used as the in-memory icon cache key. */
@Immutable
data class IconSpec(
    val title: String,
    val packages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
) {
    val cacheKey: String get() = (packages + domains).joinToString("|")
}

@Immutable
data class EntrySummary(
    val id: String,
    val type: EntryType,
    val title: String,
    val subtitle: String,
    val favorite: Boolean,
    val icon: IconSpec,
    val codesLeft: Int?,
    val codesTotal: Int?,
    val searchText: String,
)

@Immutable
data class CodeSet(val replacedAt: Long, val codes: List<RecoveryCodeEntity>)

@Immutable
data class EntryDetail(
    val entry: EntryEntity,
    val type: EntryType,
    val history: List<PasswordHistoryEntity>,
    val currentCodes: List<RecoveryCodeEntity>,
    val oldCodeSets: List<CodeSet>,
    val targets: List<LinkedTargetEntity>,
    val payload: EntryPayload = EntryPayload(),
) {
    val icon: IconSpec
        get() = IconSpec(
            title = entry.title,
            packages = targets.filter { it.kind == TargetKind.APP.db }.mapNotNull { it.packageName },
            domains = targets.filter { it.kind == TargetKind.WEB.db }.mapNotNull { it.domain },
        )
}

@Immutable
data class TargetDraft(
    val kind: TargetKind,
    val packageName: String? = null,
    val domain: String? = null,
    val label: String,
) {
    val key: String get() = "${kind.db}:${packageName ?: domain}"
}

data class EntryDraft(
    val id: String?,
    val type: EntryType,
    val title: String,
    val username: String,
    val email: String,
    val password: String,
    val notes: String,
    val favorite: Boolean,
    val targets: List<TargetDraft>,
    val codes: List<String>,
    val payload: EntryPayload = EntryPayload(),
)

/** "You used this password before" information shown while editing. */
data class ReuseInfo(
    val usedBeforeHere: PasswordHistoryEntity?,
    val otherEntries: List<String>,
)
