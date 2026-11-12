package com.personal.passvault.data.repo

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.passvault.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED black") }

data class AppSettings(
    val autoLockSeconds: Int = 30,
    val clipboardSeconds: Int = 30,
    val downloadWebIcons: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val backupReminders: Boolean = true,
    val passwordReminders: Boolean = true,
    val googleSyncReminders: Boolean = true,
    /** Ask for fingerprint / screen lock before showing or copying a secret (even while unlocked). */
    val revealNeedsAuth: Boolean = false,
)

/** Everything the background reminder check needs, read fresh from disk (the worker may run in a new process). */
data class ReminderState(
    val backupReminders: Boolean,
    val passwordReminders: Boolean,
    val backup: BackupState,
    val lastPasswordUnlockAt: Long,
    val lastBackupReminderAt: Long,
    val lastPasswordReminderAt: Long,
    val googleSyncReminders: Boolean = true,
    val lastGoogleSyncAt: Long = 0L,
    val lastGoogleSyncReminderAt: Long = 0L,
)

data class BackupState(
    val chatId: Long? = null,
    val botUsername: String? = null,
    val autoBackup: Boolean = true,
    val lastBackupAt: Long = 0L,
    val lastBackupEntries: Int = 0,
    val lastBackupMessageId: Long = 0L,
    val vaultChangedAt: Long = 0L,
    val lastError: String? = null,
    /** false only for the placeholder value before DataStore has been read. */
    val loaded: Boolean = true,
) {
    val connected: Boolean get() = chatId != null
    val hasUnbackedChanges: Boolean get() = vaultChangedAt > lastBackupAt
}

/** Non-secret settings only. Nothing in here helps an attacker read the vault. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    val settings: StateFlow<AppSettings> = store.data.map { p ->
        AppSettings(
            autoLockSeconds = p[AUTO_LOCK] ?: 30,
            clipboardSeconds = p[CLIPBOARD] ?: 30,
            downloadWebIcons = p[WEB_ICONS] ?: true,
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[DYNAMIC] ?: false,
            backupReminders = p[BACKUP_REMINDERS] ?: true,
            passwordReminders = p[PW_REMINDERS] ?: true,
            googleSyncReminders = p[GOOGLE_SYNC_REMINDERS] ?: true,
            revealNeedsAuth = p[REVEAL_NEEDS_AUTH] ?: false,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun setRevealNeedsAuth(on: Boolean) = store.edit { it[REVEAL_NEEDS_AUTH] = on }

    suspend fun setGoogleSyncReminders(on: Boolean) = store.edit { it[GOOGLE_SYNC_REMINDERS] = on }
    suspend fun recordGoogleSync() = store.edit { it[LAST_GOOGLE_SYNC_AT] = System.currentTimeMillis() }
    suspend fun markGoogleSyncReminded() = store.edit { it[LAST_GOOGLE_SYNC_REMINDER_AT] = System.currentTimeMillis() }

    // ---------- Reminder notifications ----------

    suspend fun setBackupReminders(on: Boolean) = store.edit { it[BACKUP_REMINDERS] = on }
    suspend fun setPasswordReminders(on: Boolean) = store.edit { it[PW_REMINDERS] = on }

    suspend fun reminderState(): ReminderState {
        val p = store.data.first()
        return ReminderState(
            backupReminders = p[BACKUP_REMINDERS] ?: true,
            passwordReminders = p[PW_REMINDERS] ?: true,
            backup = BackupState(
                chatId = p[TG_CHAT_ID], botUsername = p[TG_BOT_USERNAME], autoBackup = p[AUTO_BACKUP] ?: true,
                lastBackupAt = p[LAST_BACKUP_AT] ?: 0L, lastBackupEntries = p[LAST_BACKUP_ENTRIES] ?: 0,
                lastBackupMessageId = p[LAST_BACKUP_MSG] ?: 0L, vaultChangedAt = p[VAULT_CHANGED_AT] ?: 0L,
                lastError = p[LAST_BACKUP_ERROR],
            ),
            lastPasswordUnlockAt = p[LAST_PW_UNLOCK_AT] ?: 0L,
            lastBackupReminderAt = p[LAST_BACKUP_REMINDER_AT] ?: 0L,
            lastPasswordReminderAt = p[LAST_PW_REMINDER_AT] ?: 0L,
            googleSyncReminders = p[GOOGLE_SYNC_REMINDERS] ?: true,
            lastGoogleSyncAt = p[LAST_GOOGLE_SYNC_AT] ?: 0L,
            lastGoogleSyncReminderAt = p[LAST_GOOGLE_SYNC_REMINDER_AT] ?: 0L,
        )
    }

    suspend fun markBackupReminded() = store.edit { it[LAST_BACKUP_REMINDER_AT] = System.currentTimeMillis() }
    suspend fun markPasswordReminded() = store.edit { it[LAST_PW_REMINDER_AT] = System.currentTimeMillis() }

    /** Ask for notification permission once (Android 13+); after that it's in Settings → Reminders. */
    suspend fun shouldAskNotificationPermission(): Boolean {
        val asked = store.data.first()[NOTIF_ASKED] ?: false
        if (!asked) store.edit { it[NOTIF_ASKED] = true }
        return !asked
    }

    // ---------- Telegram backup status (non-secret; the token itself lives in TokenStore) ----------

    val backup: StateFlow<BackupState> = store.data.map { p ->
        BackupState(
            chatId = p[TG_CHAT_ID],
            botUsername = p[TG_BOT_USERNAME],
            autoBackup = p[AUTO_BACKUP] ?: true,
            lastBackupAt = p[LAST_BACKUP_AT] ?: 0L,
            lastBackupEntries = p[LAST_BACKUP_ENTRIES] ?: 0,
            lastBackupMessageId = p[LAST_BACKUP_MSG] ?: 0L,
            vaultChangedAt = p[VAULT_CHANGED_AT] ?: 0L,
            lastError = p[LAST_BACKUP_ERROR],
        )
    }.stateIn(scope, SharingStarted.Eagerly, BackupState(loaded = false))

    suspend fun currentBackup(): BackupState = backup.value.takeIf { it.loaded } ?: run {
        val p = store.data.first()
        BackupState(
            chatId = p[TG_CHAT_ID], botUsername = p[TG_BOT_USERNAME], autoBackup = p[AUTO_BACKUP] ?: true,
            lastBackupAt = p[LAST_BACKUP_AT] ?: 0L, lastBackupEntries = p[LAST_BACKUP_ENTRIES] ?: 0,
            lastBackupMessageId = p[LAST_BACKUP_MSG] ?: 0L, vaultChangedAt = p[VAULT_CHANGED_AT] ?: 0L,
            lastError = p[LAST_BACKUP_ERROR],
        )
    }

    suspend fun setTelegram(chatId: Long?, botUsername: String?) = store.edit {
        if (chatId == null) it.remove(TG_CHAT_ID) else it[TG_CHAT_ID] = chatId
        if (botUsername == null) it.remove(TG_BOT_USERNAME) else it[TG_BOT_USERNAME] = botUsername
        Unit
    }

    suspend fun setAutoBackup(on: Boolean) = store.edit { it[AUTO_BACKUP] = on }

    suspend fun markVaultChanged() = store.edit { it[VAULT_CHANGED_AT] = System.currentTimeMillis() }

    suspend fun recordBackup(at: Long, entries: Int, messageId: Long) = store.edit {
        it[LAST_BACKUP_AT] = at
        it[LAST_BACKUP_ENTRIES] = entries
        it[LAST_BACKUP_MSG] = messageId
        it.remove(LAST_BACKUP_ERROR)
    }

    suspend fun recordBackupError(message: String?) = store.edit {
        if (message == null) it.remove(LAST_BACKUP_ERROR) else it[LAST_BACKUP_ERROR] = message
        Unit
    }

    suspend fun setAutoLock(seconds: Int) = store.edit { it[AUTO_LOCK] = seconds }
    suspend fun setClipboard(seconds: Int) = store.edit { it[CLIPBOARD] = seconds }
    suspend fun setDownloadWebIcons(on: Boolean) = store.edit { it[WEB_ICONS] = on }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = store.edit { it[DYNAMIC] = on }

    // ---------- Unlock guard (wrong attempts + "ask for master password regularly") ----------

    data class Guard(val failedAttempts: Int, val lockedUntil: Long, val passwordRequired: Boolean)

    suspend fun guard(): Guard {
        val p = store.data.first()
        val lastPwAt = p[LAST_PW_UNLOCK_AT] ?: 0L
        val lastPwBoot = p[LAST_PW_UNLOCK_BOOT] ?: -1
        val required = lastPwAt == 0L ||
            System.currentTimeMillis() - lastPwAt > PASSWORD_EVERY_MS ||
            lastPwBoot != bootCount()
        return Guard(p[FAILED] ?: 0, p[LOCKED_UNTIL] ?: 0L, required)
    }

    /** Returns the new lock-out end time (0 if none). */
    suspend fun recordFailedAttempt(): Long {
        var until = 0L
        store.edit {
            val failed = (it[FAILED] ?: 0) + 1
            it[FAILED] = failed
            val delayMs = when {
                failed < 5 -> 0L
                failed == 5 -> 30_000L
                failed == 6 -> 60_000L
                else -> 5 * 60_000L
            }
            until = if (delayMs > 0) System.currentTimeMillis() + delayMs else 0L
            it[LOCKED_UNTIL] = until
        }
        return until
    }

    suspend fun recordSuccessfulUnlock(withPassword: Boolean) {
        store.edit {
            it[FAILED] = 0
            it[LOCKED_UNTIL] = 0L
            if (withPassword) {
                it[LAST_PW_UNLOCK_AT] = System.currentTimeMillis()
                it[LAST_PW_UNLOCK_BOOT] = bootCount()
            }
        }
    }

    suspend fun clearAll() = store.edit { it.clear() }

    private fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private companion object {
        val AUTO_LOCK = intPreferencesKey("auto_lock_seconds")
        val CLIPBOARD = intPreferencesKey("clipboard_seconds")
        val WEB_ICONS = booleanPreferencesKey("download_web_icons")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val FAILED = intPreferencesKey("failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("locked_until")
        val LAST_PW_UNLOCK_AT = longPreferencesKey("last_password_unlock_at")
        val LAST_PW_UNLOCK_BOOT = intPreferencesKey("last_password_unlock_boot")
        val TG_CHAT_ID = longPreferencesKey("tg_chat_id")
        val TG_BOT_USERNAME = stringPreferencesKey("tg_bot_username")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_ENTRIES = intPreferencesKey("last_backup_entries")
        val LAST_BACKUP_MSG = longPreferencesKey("last_backup_message_id")
        val VAULT_CHANGED_AT = longPreferencesKey("vault_changed_at")
        val LAST_BACKUP_ERROR = stringPreferencesKey("last_backup_error")
        val BACKUP_REMINDERS = booleanPreferencesKey("backup_reminders")
        val PW_REMINDERS = booleanPreferencesKey("password_reminders")
        val LAST_BACKUP_REMINDER_AT = longPreferencesKey("last_backup_reminder_at")
        val LAST_PW_REMINDER_AT = longPreferencesKey("last_password_reminder_at")
        val NOTIF_ASKED = booleanPreferencesKey("notification_permission_asked")
        val GOOGLE_SYNC_REMINDERS = booleanPreferencesKey("google_sync_reminders")
        val REVEAL_NEEDS_AUTH = booleanPreferencesKey("reveal_needs_auth")
        val LAST_GOOGLE_SYNC_AT = longPreferencesKey("last_google_sync_at")
        val LAST_GOOGLE_SYNC_REMINDER_AT = longPreferencesKey("last_google_sync_reminder_at")
        const val PASSWORD_EVERY_MS = 7L * 24 * 60 * 60 * 1000
    }
}
