package com.personal.passvault.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.personal.passvault.BuildConfig
import com.personal.passvault.core.crypto.BackupCodec
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.db.DatabaseProvider
import com.personal.passvault.data.db.EntryEntity
import com.personal.passvault.data.db.LinkedTargetEntity
import com.personal.passvault.data.db.PasswordHistoryEntity
import com.personal.passvault.data.db.RecoveryCodeEntity
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.data.telegram.TelegramClient
import com.personal.passvault.data.telegram.TelegramException
import com.personal.passvault.data.telegram.TgUser
import com.personal.passvault.data.telegram.TokenStore
import com.personal.passvault.di.ApplicationScope
import com.personal.passvault.domain.TimeText
import com.personal.passvault.work.BackupUploadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Everything inside a backup (before encryption). Website icons are left out — they're re-downloaded. */
data class BackupPayload(
    val format: Int = 1,
    val createdAt: Long,
    val appVersion: String,
    val entries: List<EntryEntity>,
    val history: List<PasswordHistoryEntity>,
    val codes: List<RecoveryCodeEntity>,
    val targets: List<LinkedTargetEntity>,
)

data class PendingInfo(val createdAt: Long, val entries: Int)

sealed interface UploadResult {
    data class Success(val at: Long, val entries: Int) : UploadResult
    data object NotConfigured : UploadResult
    data object NothingPending : UploadResult
    data class Failed(val message: String, val retryable: Boolean) : UploadResult
}

enum class RestoreMode { REPLACE, MERGE }

data class ImportResult(val added: Int, val updated: Int, val keptLocal: Int)

class NoBackupFoundException : Exception("No pinned backup found in your bot chat")

/**
 * Creates encrypted `.pvlt` backups, sends them to the user's own Telegram bot, and restores them.
 *
 * Flow for automatic backups: every change -> [onVaultChanged] -> the backup is encrypted *immediately*
 * while the vault is unlocked and written to a pending file -> a WorkManager job uploads it when online
 * (1 minute later, so a burst of edits becomes one backup). The upload never needs the vault unlocked.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbProvider: DatabaseProvider,
    private val session: VaultSession,
    private val keyManager: VaultKeyManager,
    private val settings: SettingsRepository,
    private val telegram: TelegramClient,
    private val tokenStore: TokenStore,
    private val gson: Gson,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val pendingFile = File(context.noBackupFilesDir, "pending.pvlt")
    private val pendingInfoFile = File(context.noBackupFilesDir, "pending.json")
    private val uploadMutex = Mutex()
    private val prepareMutex = Mutex()

    val isTelegramReady: Boolean get() = tokenStore.hasToken() && settings.backup.value.connected

    // =============================================================================================
    // Creating backups
    // =============================================================================================

    /** Called by the repository after every change to the vault. */
    fun onVaultChanged() {
        // The decoy vault must never be uploaded: it would replace the real pinned backup.
        if (session.isDecoy) return
        scope.launch {
            settings.markVaultChanged()
            val state = settings.currentBackup()
            if (state.connected && state.autoBackup && tokenStore.hasToken()) {
                if (runCatching { preparePending() }.isSuccess) {
                    BackupUploadWorker.enqueue(context, delaySeconds = 60)
                }
            }
        }
    }

    /** Encrypted backup of the whole vault. Vault must be unlocked. */
    suspend fun buildBackup(): Pair<ByteArray, PendingInfo> = withContext(Dispatchers.IO) {
        val db = dbProvider.require()
        val payload = BackupPayload(
            createdAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            entries = db.entries().getAll(),
            history = db.history().getAll(),
            codes = db.codes().getAll(),
            targets = db.targets().getAll(),
        )
        val key = session.withVaultKey { vk -> keyManager.backupKey(vk) }
            ?: throw IllegalStateException("Unlock once with your master password to turn on backups")
        try {
            val json = gson.toJson(payload).toByteArray(Charsets.UTF_8)
            BackupCodec.encrypt(json, key) to PendingInfo(payload.createdAt, payload.entries.size)
        } finally {
            key.wipe()
        }
    }

    private suspend fun preparePending() = prepareMutex.withLock {
        val (bytes, info) = buildBackup()
        withContext(Dispatchers.IO) {
            writeAtomic(pendingFile, bytes)
            writeAtomic(pendingInfoFile, gson.toJson(info).toByteArray())
        }
    }

    /** "Back up now" button. */
    suspend fun backupNow(): UploadResult {
        if (!isTelegramReady) return UploadResult.NotConfigured
        // Decoy vault: look like a network hiccup rather than revealing anything
        if (session.isDecoy) return UploadResult.Failed("Couldn't reach Telegram — will retry when online", retryable = true)
        return try {
            preparePending()
            uploadPending()
        } catch (e: Exception) {
            UploadResult.Failed(e.message ?: "Backup failed", retryable = false)
        }
    }

    /** Uploads the pending encrypted file. Safe to call while the vault is locked (used by the worker). */
    suspend fun uploadPending(): UploadResult = uploadMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!pendingFile.exists()) return@withContext UploadResult.NothingPending
            val token = tokenStore.load() ?: return@withContext UploadResult.NotConfigured
            val state = settings.currentBackup()
            val chatId = state.chatId ?: return@withContext UploadResult.NotConfigured
            val bytes = pendingFile.readBytes()
            val info = runCatching { gson.fromJson(pendingInfoFile.readText(), PendingInfo::class.java) }.getOrNull()
                ?: PendingInfo(pendingFile.lastModified(), 0)
            try {
                val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(info.createdAt))
                val caption = "🔐 PassVault backup · ${info.entries} entries · ${TimeText.dateTime(info.createdAt)}\n" +
                    "Encrypted — only your master password can open it."
                val msg = telegram.sendDocument(token, chatId, bytes, "passvault-$stamp.${BackupCodec.FILE_EXTENSION}", caption)
                // Pin the newest backup so a new phone can always find it; unpin the previous one
                runCatching { telegram.pinChatMessage(token, chatId, msg.messageId) }
                if (state.lastBackupMessageId != 0L && state.lastBackupMessageId != msg.messageId) {
                    runCatching { telegram.unpinChatMessage(token, chatId, state.lastBackupMessageId) }
                }
                settings.recordBackup(info.createdAt, info.entries, msg.messageId)
                // Only remove the pending file if a newer one wasn't written while we were uploading
                if (pendingFile.exists() && pendingFile.readBytes().contentEquals(bytes)) {
                    pendingFile.delete()
                    pendingInfoFile.delete()
                }
                UploadResult.Success(info.createdAt, info.entries)
            } catch (e: TelegramException) {
                settings.recordBackupError(e.message)
                UploadResult.Failed(e.message ?: "Telegram error", retryable = e.code == 429 || e.code >= 500)
            } catch (e: IOException) {
                settings.recordBackupError("Couldn't reach Telegram — will retry when online")
                UploadResult.Failed("Couldn't reach Telegram — will retry when online", retryable = true)
            }
        }
    }

    /** Writes an encrypted backup to a file the user picked (Downloads, Drive, USB…). */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val (bytes, info) = buildBackup()
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
            ?: throw IOException("Couldn't open the file for writing")
        info.entries
    }

    // =============================================================================================
    // Telegram connection
    // =============================================================================================

    suspend fun verifyToken(token: String): TgUser {
        val me = telegram.getMe(token.trim())
        if (!me.isBot) throw TelegramException("That token doesn't belong to a bot", 400)
        return me
    }

    /** Marks everything already waiting in the bot's queue as read and returns the next offset. */
    suspend fun startPairing(token: String): Long =
        (telegram.getUpdates(token).maxOfOrNull { it.updateId } ?: -1L) + 1

    /**
     * Polls for "/start <code>" sent from a private chat. Returns that chat's id, or null on timeout.
     * (Doesn't work while a bot server or webhook is consuming updates — then the chat ID is typed in.)
     */
    suspend fun waitForPairing(token: String, code: String, firstOffset: Long, timeoutMs: Long = 180_000): Long? {
        var offset = firstOffset
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val updates = telegram.getUpdates(token, offset)
            for (u in updates) {
                val msg = u.message ?: continue
                val chat = msg.chat ?: continue
                val text = msg.text.orEmpty().trim()
                if (chat.type == "private" && text.startsWith("/start") && text.contains(code)) {
                    runCatching { telegram.getUpdates(token, u.updateId + 1) } // acknowledge
                    return chat.id
                }
            }
            updates.maxOfOrNull { it.updateId }?.let { offset = it + 1 }
            delay(2_000)
        }
        return null
    }

    /** Saves the token + chat and sends a confirmation message (which also proves the chat ID is right). */
    suspend fun connect(token: String, chatId: Long, botUsername: String?) {
        // Decoy vault: never let anyone redirect the real backups to another bot
        if (session.isDecoy) throw IOException("Couldn't reach Telegram — try again later")
        telegram.sendMessage(
            token.trim(), chatId,
            "✅ PassVault connected.\n\nEncrypted backups of your vault will be sent to this chat and the newest one is pinned. " +
                "They can only be opened with your master password.\n\nDon't delete this chat.",
        )
        tokenStore.save(token.trim())
        settings.setTelegram(chatId, botUsername)
    }

    suspend fun disconnect() {
        if (session.isDecoy) return
        tokenStore.clear()
        settings.setTelegram(null, null)
        withContext(Dispatchers.IO) {
            pendingFile.delete()
            pendingInfoFile.delete()
        }
    }

    // =============================================================================================
    // Restoring
    // =============================================================================================

    /** Downloads the pinned (newest) backup from the bot chat. */
    suspend fun downloadLatest(): ByteArray {
        val token = tokenStore.load() ?: throw IllegalStateException("Telegram isn't connected")
        val chatId = settings.currentBackup().chatId ?: throw IllegalStateException("Telegram isn't connected")
        val doc = telegram.getChat(token, chatId).pinnedMessage?.document ?: throw NoBackupFoundException()
        val path = telegram.getFile(token, doc.fileId).filePath ?: throw IOException("Telegram didn't return the file")
        return telegram.downloadFile(token, path)
    }

    suspend fun readFile(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > 20 * 1024 * 1024) throw IOException("File is too large to be a PassVault backup")
            bytes
        } ?: throw IOException("Couldn't open the file")
    }

    /** Decrypts with the master password that was in use when the backup was made. Slow (Argon2id). */
    suspend fun open(file: ByteArray, master: CharArray): BackupPayload = withContext(Dispatchers.Default) {
        val json = BackupCodec.decryptWithPassword(file, master)
        gson.fromJson(String(json, Charsets.UTF_8), BackupPayload::class.java)
            ?: throw IOException("Backup is empty")
    }

    /**
     * REPLACE: the vault becomes exactly the backup.
     * MERGE: per entry the newer version wins, and passwords are never lost — every password from either side
     * that isn't current ends up in that entry's history.
     */
    suspend fun import(payload: BackupPayload, mode: RestoreMode): ImportResult = withContext(Dispatchers.IO) {
        val db = dbProvider.require()
        val now = System.currentTimeMillis()
        var added = 0
        var updated = 0
        var kept = 0
        val historyBy = payload.history.groupBy { it.entryId }
        val codesBy = payload.codes.groupBy { it.entryId }
        val targetsBy = payload.targets.groupBy { it.entryId }

        // Backups made before DB v2 have no "payload" field -> Gson leaves it null despite the Kotlin type
        @Suppress("USELESS_CAST")
        val entries = payload.entries.map { it.copy(payload = (it.payload as String?) ?: "") }

        db.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                db.entries().deleteAll()
                entries.forEach { db.entries().upsert(it) }
                db.history().insertAll(payload.history.map { it.copy(id = 0) })
                db.codes().insertAll(payload.codes.map { it.copy(id = 0) })
                db.targets().insertAll(payload.targets.map { it.copy(id = 0) })
                added = entries.size
                return@withTransaction
            }

            for (e in entries) {
                val local = db.entries().get(e.id)
                val backupHistory = historyBy[e.id].orEmpty()
                if (local == null) {
                    db.entries().upsert(e)
                    db.history().insertAll(backupHistory.map { it.copy(id = 0) })
                    db.codes().insertAll(codesBy[e.id].orEmpty().map { it.copy(id = 0) })
                    db.targets().insertAll(targetsBy[e.id].orEmpty().map { it.copy(id = 0) })
                    added++
                    continue
                }
                val localHistory = db.history().get(e.id)
                val known = (localHistory + backupHistory).map { it.password }.toMutableSet()

                if (e.updatedAt > local.updatedAt) {
                    // Backup wins — but keep this phone's current password in history
                    if (local.password.isNotEmpty() && local.password != e.password && local.password !in known) {
                        db.history().insert(
                            PasswordHistoryEntity(
                                entryId = e.id, password = local.password, usedFrom = local.passwordChangedAt,
                                replacedAt = now, note = "Kept from this phone during restore",
                            )
                        )
                    }
                    db.entries().upsert(e)
                    db.targets().clear(e.id)
                    db.targets().insertAll(targetsBy[e.id].orEmpty().map { it.copy(id = 0) })
                    db.codes().deleteAllFor(e.id)
                    db.codes().insertAll(codesBy[e.id].orEmpty().map { it.copy(id = 0) })
                    updated++
                } else {
                    // This phone wins — but keep the backup's current password in history
                    if (e.password.isNotEmpty() && e.password != local.password && e.password !in known) {
                        db.history().insert(
                            PasswordHistoryEntity(
                                entryId = e.id, password = e.password, usedFrom = e.passwordChangedAt,
                                replacedAt = now, note = "From backup of ${TimeText.date(payload.createdAt)}",
                            )
                        )
                    }
                    kept++
                }
                // Union of both histories
                val localKeys = localHistory.map { it.password to it.replacedAt }.toSet()
                db.history().insertAll(
                    backupHistory.filter { (it.password to it.replacedAt) !in localKeys }.map { it.copy(id = 0) }
                )
            }
        }
        onVaultChanged()
        ImportResult(added, updated, kept)
    }

    /** Wipe support: forget everything about backups on this phone (not the backups in Telegram). */
    suspend fun forgetLocal() = withContext(Dispatchers.IO) {
        pendingFile.delete()
        pendingInfoFile.delete()
        tokenStore.clear()
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }
}
