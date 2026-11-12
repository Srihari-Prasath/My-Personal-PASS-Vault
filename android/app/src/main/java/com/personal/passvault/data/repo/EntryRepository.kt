package com.personal.passvault.data.repo

import androidx.room.withTransaction
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.backup.BackupManager
import com.personal.passvault.data.db.DatabaseProvider
import com.personal.passvault.data.db.EntryEntity
import com.personal.passvault.data.db.LinkedTargetEntity
import com.personal.passvault.data.db.PasswordHistoryEntity
import com.personal.passvault.data.db.RecoveryCodeEntity
import com.personal.passvault.data.db.VaultDatabase
import com.personal.passvault.domain.CodeSet
import com.personal.passvault.domain.EntryDetail
import com.personal.passvault.domain.EntryDraft
import com.personal.passvault.domain.EntrySummary
import com.personal.passvault.domain.EntryPayload
import com.personal.passvault.domain.EntryType
import com.personal.passvault.domain.IconSpec
import com.personal.passvault.domain.ReuseInfo
import com.personal.passvault.domain.TargetKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val dbProvider: DatabaseProvider,
    private val session: VaultSession,
    private val backups: BackupManager,
) {
    /** Every write goes through here so an encrypted backup is queued automatically. */
    private suspend fun <T> change(block: suspend () -> T): T = block().also { backups.onVaultChanged() }

    /** Emits [default] while locked; switches to live DB data while unlocked. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> whileUnlocked(default: T, block: (VaultDatabase) -> Flow<T>): Flow<T> =
        session.unlocked.flatMapLatest { unlocked ->
            if (!unlocked) flowOf(default)
            else block(dbProvider.require()).catch { emit(default) } // DB closed mid-query on lock
        }

    /** Last list shown, so the detail screen can draw its header instantly (smooth shared-element morph). */
    @Volatile
    private var summaryCache: Map<String, EntrySummary> = emptyMap()

    fun cachedSummary(id: String): EntrySummary? = summaryCache[id]

    fun summaries(): Flow<List<EntrySummary>> = summariesFromDb().onEach { list ->
        summaryCache = list.associateBy { it.id }
    }

    private fun summariesFromDb(): Flow<List<EntrySummary>> = whileUnlocked(emptyList()) { db ->
        combine(
            db.entries().observeAll(),
            db.targets().observeAll(),
            db.codes().observeAll(),
        ) { entries, targets, codes ->
            val targetsByEntry = targets.groupBy { it.entryId }
            val codesByEntry = codes.filter { it.setReplacedAt == null }.groupBy { it.entryId }
            entries.map { e ->
                val type = EntryType.from(e.type)
                val t = targetsByEntry[e.id].orEmpty()
                val c = codesByEntry[e.id].orEmpty()
                val left = c.count { !it.used }
                val p = EntryPayload.parse(e.payload)
                EntrySummary(
                    id = e.id,
                    type = type,
                    title = e.title,
                    subtitle = when (type) {
                        EntryType.LOGIN -> e.username.ifBlank { e.email }.ifBlank { e.url }.ifBlank { "No username" } +
                            if (p.totp != null) " · 2FA" else ""
                        EntryType.RECOVERY_CODES -> if (c.isEmpty()) "No codes yet" else "$left of ${c.size} codes left"
                        EntryType.NOTE -> "Secure note"
                        EntryType.TOTP -> p.totp?.let { it.account.ifBlank { it.issuer }.ifBlank { "2FA codes" } } ?: "No secret yet"
                        EntryType.CARD -> p.card?.let { "${it.network.label} •••• ${it.last4}".trim() + if (it.expiry.isNotBlank()) " · ${it.expiry}" else "" } ?: "Card"
                        EntryType.WIFI -> p.wifi?.ssid?.ifBlank { null } ?: "Wi-Fi network"
                    },
                    favorite = e.favorite,
                    icon = IconSpec(
                        title = e.title,
                        packages = t.filter { it.kind == TargetKind.APP.db }.mapNotNull { it.packageName },
                        domains = t.filter { it.kind == TargetKind.WEB.db }.mapNotNull { it.domain },
                    ),
                    codesLeft = if (type == EntryType.RECOVERY_CODES) left else null,
                    codesTotal = if (type == EntryType.RECOVERY_CODES) c.size else null,
                    searchText = listOf(
                        e.title, e.username, e.email, e.url, e.tags, t.joinToString(" ") { it.label },
                        p.totp?.issuer.orEmpty(), p.card?.bank.orEmpty(), p.card?.network?.label.orEmpty(),
                        p.wifi?.ssid.orEmpty(),
                    ).joinToString(" ").lowercase(),
                )
            }
        }
    }

    fun detail(id: String): Flow<EntryDetail?> = whileUnlocked(null) { db ->
        combine(
            db.entries().observe(id),
            db.history().observe(id),
            db.codes().observe(id),
            db.targets().observe(id),
        ) { entry, history, codes, targets ->
            entry ?: return@combine null
            EntryDetail(
                entry = entry,
                type = EntryType.from(entry.type),
                history = history,
                currentCodes = codes.filter { it.setReplacedAt == null },
                oldCodeSets = codes.filter { it.setReplacedAt != null }
                    .groupBy { it.setReplacedAt!! }
                    .map { (at, list) -> CodeSet(at, list.sortedBy { it.position }) }
                    .sortedByDescending { it.replacedAt },
                targets = targets,
                payload = EntryPayload.parse(entry.payload),
            )
        }
    }

    // ---------- Security check, autofill, import ----------

    data class AuditData(
        val entries: List<EntryEntity>,
        val codes: List<RecoveryCodeEntity>,
        val targets: List<LinkedTargetEntity>,
    )

    /** Everything the security dashboard needs, in one read. */
    suspend fun auditData(): AuditData {
        val db = dbProvider.require()
        return AuditData(db.entries().getAll(), db.codes().getAll(), db.targets().getAll())
    }

    /** Entries linked to an app package or a website domain (for Autofill). Subdomains match their parent. */
    suspend fun entriesFor(packageName: String?, webDomain: String?): List<EntryEntity> {
        val db = dbProvider.require()
        val targets = db.targets().getAll()
        val domain = webDomain?.let { com.personal.passvault.domain.Domains.normalize(it) }
        val ids = targets.filter { t ->
            (packageName != null && t.kind == TargetKind.APP.db && t.packageName == packageName) ||
                (domain != null && t.kind == TargetKind.WEB.db && t.domain != null &&
                    (domain == t.domain || domain.endsWith("." + t.domain)))
        }.map { it.entryId }.toSet()
        return db.entries().getAll().filter { it.id in ids }
    }

    /** Bulk insert (CSV import): one transaction, one backup afterwards. */
    suspend fun saveAll(drafts: List<EntryDraft>): Int = change {
        val db = dbProvider.require()
        db.withTransaction { drafts.forEach { saveInternal(it) } }
        drafts.size
    }

    /**
     * Sync from Google: add new logins and give existing entries their new password, in one transaction.
     * Each replaced password goes into that entry's history (via saveInternal), so nothing is ever lost.
     */
    suspend fun importSync(newDrafts: List<EntryDraft>, passwordUpdates: Map<String, String>): Int = change {
        val db = dbProvider.require()
        var updated = 0
        db.withTransaction {
            newDrafts.forEach { saveInternal(it) }
            for ((id, password) in passwordUpdates) {
                val e = db.entries().get(id) ?: continue
                if (e.password == password) continue
                saveInternal(draftOf(e).copy(password = password))
                updated++
            }
        }
        updated
    }

    /** An editable copy of an existing entry with all its parts (targets, codes, payload) kept. */
    private suspend fun draftOf(e: EntryEntity): EntryDraft {
        val db = dbProvider.require()
        return EntryDraft(
            id = e.id,
            type = EntryType.from(e.type),
            title = e.title,
            username = e.username,
            email = e.email,
            password = e.password,
            notes = e.notes,
            favorite = e.favorite,
            targets = db.targets().get(e.id).map {
                com.personal.passvault.domain.TargetDraft(TargetKind.from(it.kind), it.packageName, it.domain, it.label)
            },
            codes = db.codes().currentSet(e.id).map { it.code },
            payload = EntryPayload.parse(e.payload),
        )
    }

    suspend fun get(id: String): EntryEntity? = dbProvider.require().entries().get(id)
    suspend fun targets(id: String): List<LinkedTargetEntity> = dbProvider.require().targets().get(id)
    suspend fun currentCodes(id: String): List<RecoveryCodeEntity> = dbProvider.require().codes().currentSet(id)

    /**
     * Saves an entry. If the password changed, the OLD password is written to history first
     * (with the period it was in use), so no password is ever lost.
     */
    suspend fun save(draft: EntryDraft): String = change { saveInternal(draft) }

    private suspend fun saveInternal(draft: EntryDraft): String {
        val db = dbProvider.require()
        val now = System.currentTimeMillis()
        val id = draft.id ?: UUID.randomUUID().toString()
        db.withTransaction {
            val existing = db.entries().get(id)
            var passwordChangedAt = existing?.passwordChangedAt ?: now
            if (existing != null && existing.password != draft.password) {
                if (existing.password.isNotEmpty()) {
                    db.history().insert(
                        PasswordHistoryEntity(
                            entryId = id,
                            password = existing.password,
                            usedFrom = existing.passwordChangedAt,
                            replacedAt = now,
                        )
                    )
                }
                passwordChangedAt = now
            }
            val domains = draft.targets.filter { it.kind == TargetKind.WEB }.mapNotNull { it.domain }
            db.entries().upsert(
                EntryEntity(
                    id = id,
                    type = draft.type.name,
                    title = draft.title.trim(),
                    username = draft.username.trim(),
                    email = draft.email.trim(),
                    password = draft.password,
                    url = domains.firstOrNull().orEmpty(),
                    notes = draft.notes,
                    favorite = draft.favorite,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    passwordChangedAt = passwordChangedAt,
                    payload = draft.payload.toJson(),
                )
            )
            db.targets().clear(id)
            db.targets().insertAll(
                draft.targets.mapIndexed { i, t ->
                    LinkedTargetEntity(
                        entryId = id, kind = t.kind.db, packageName = t.packageName,
                        domain = t.domain, label = t.label, position = i,
                    )
                }
            )
            if (draft.type == EntryType.RECOVERY_CODES) syncCodes(db, id, draft.codes, now)
        }
        return id
    }

    /**
     * Same codes edited -> keep "used" ticks. A completely new set (no code in common) ->
     * the old set is archived as history ("Replaced on …") instead of being deleted.
     */
    private suspend fun syncCodes(db: VaultDatabase, entryId: String, codes: List<String>, now: Long) {
        val newCodes = codes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val current = db.codes().currentSet(entryId)
        if (current.map { it.code } == newCodes) return
        val overlap = current.count { it.code in newCodes }
        val keepTicks = overlap > 0
        if (current.isNotEmpty() && !keepTicks) db.codes().archiveCurrentSet(entryId, now)
        else db.codes().deleteCurrentSet(entryId)
        val old = current.associateBy { it.code }
        db.codes().insertAll(
            newCodes.mapIndexed { i, code ->
                val prev = if (keepTicks) old[code] else null
                RecoveryCodeEntity(
                    entryId = entryId, code = code, used = prev?.used ?: false,
                    usedAt = prev?.usedAt, position = i,
                )
            }
        )
    }

    /** Makes an old password current again; the current one moves into history. */
    suspend fun restorePassword(entryId: String, historyId: Long) = change { restoreInternal(entryId, historyId) }

    private suspend fun restoreInternal(entryId: String, historyId: Long) {
        val db = dbProvider.require()
        val now = System.currentTimeMillis()
        db.withTransaction {
            val entry = db.entries().get(entryId) ?: return@withTransaction
            val old = db.history().getById(historyId) ?: return@withTransaction
            if (entry.password == old.password) return@withTransaction
            if (entry.password.isNotEmpty()) {
                db.history().insert(
                    PasswordHistoryEntity(
                        entryId = entryId,
                        password = entry.password,
                        usedFrom = entry.passwordChangedAt,
                        replacedAt = now,
                        note = "Replaced by restoring an older password",
                    )
                )
            }
            db.entries().upsert(entry.copy(password = old.password, passwordChangedAt = now, updatedAt = now))
        }
    }

    suspend fun deleteHistoryItem(id: Long) = change { dbProvider.require().history().delete(id) }

    suspend fun setCodeUsed(codeId: Long, used: Boolean) = change {
        dbProvider.require().codes().setUsed(codeId, used, if (used) System.currentTimeMillis() else null)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = change {
        dbProvider.require().entries().setFavorite(id, favorite, System.currentTimeMillis())
    }

    suspend fun delete(id: String) = change { dbProvider.require().entries().delete(id) }

    suspend fun reuseInfo(entryId: String?, password: String): ReuseInfo {
        if (password.isEmpty()) return ReuseInfo(null, emptyList())
        val db = dbProvider.require()
        val before = entryId?.let { id -> db.history().get(id).firstOrNull { it.password == password } }
        val others = db.entries().entriesUsingPassword(password, entryId ?: "").map { it.title }
        return ReuseInfo(before, others)
    }
}
