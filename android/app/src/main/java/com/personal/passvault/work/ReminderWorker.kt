package com.personal.passvault.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.passvault.MainActivity
import com.personal.passvault.R
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.data.repo.ReminderState
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.domain.TimeText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun settings(): SettingsRepository
    fun keyManager(): VaultKeyManager
}

/**
 * Replaces the bot server's reminders with local notifications — no server, works offline.
 * Runs every 12 hours; each reminder type is throttled so it never nags more than every few days.
 * Notifications never contain secrets or account names.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)
        if (!ep.keyManager().vaultExists()) return Result.success()
        val settings = ep.settings()
        val state = settings.reminderState()
        val now = System.currentTimeMillis()

        if (state.backupReminders && now - state.lastBackupReminderAt > BACKUP_THROTTLE_MS) {
            backupMessage(state, now)?.let { (title, text) ->
                if (notify(applicationContext, ID_BACKUP, title, text)) settings.markBackupReminded()
            }
        }

        if (state.passwordReminders &&
            state.lastPasswordUnlockAt > 0 &&
            now - state.lastPasswordUnlockAt > PASSWORD_AFTER_MS &&
            now - state.lastPasswordReminderAt > PASSWORD_THROTTLE_MS
        ) {
            val ok = notify(
                applicationContext, ID_PASSWORD,
                "Do you still remember your master password?",
                "You haven't typed it for ${TimeText.duration(state.lastPasswordUnlockAt, now)}. " +
                    "Open PassVault and unlock with the password (not fingerprint) so you don't forget it.",
            )
            if (ok) settings.markPasswordReminded()
        }

        // Monthly "sync from Google": 30 days after the last import (or the last reminder, so it's never naggy)
        if (state.googleSyncReminders &&
            now - maxOf(state.lastGoogleSyncAt, state.lastGoogleSyncReminderAt) > GOOGLE_SYNC_EVERY_MS
        ) {
            val ok = notify(
                applicationContext, ID_GOOGLE_SYNC,
                "Time to sync from Google",
                "2 minutes: passwords.google.com → Settings → Export, then PassVault → Settings → Import passwords. " +
                    "New logins are added and changed passwords updated.",
            )
            if (ok) settings.markGoogleSyncReminded()
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "passvault_reminders"
        private const val CHANNEL_ID = "reminders"
        private const val ID_BACKUP = 1001
        private const val ID_PASSWORD = 1002
        private const val ID_TEST = 1003
        private const val ID_GOOGLE_SYNC = 1004
        private const val GOOGLE_SYNC_EVERY_MS = 30L * 24 * 60 * 60 * 1000

        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val BACKUP_THROTTLE_MS = 3 * DAY_MS
        private const val PASSWORD_AFTER_MS = 30 * DAY_MS
        private const val PASSWORD_THROTTLE_MS = 7 * DAY_MS

        /** Called on every app start; KEEP = doesn't reset the schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** What (if anything) to say about backups. Pure logic — unit tested. */
        fun backupMessage(state: ReminderState, now: Long): Pair<String, String>? {
            val b = state.backup
            return when {
                // Has data, never connected Telegram
                !b.connected && b.vaultChangedAt > 0 -> "Your vault isn't backed up" to
                    "If you lose this phone, your passwords are gone. Open PassVault → Settings → Backup & restore."
                !b.connected -> null
                // Uploads are failing
                b.hasUnbackedChanges && b.lastError != null && now - b.vaultChangedAt > DAY_MS ->
                    "Vault backup isn't uploading" to "Last error: ${b.lastError}. Open PassVault → Backup & restore."
                // Changes waiting (auto-backup off, or the phone was offline for days)
                b.hasUnbackedChanges && now - b.lastBackupAt > 2 * DAY_MS && now - b.vaultChangedAt > DAY_MS ->
                    "Recent changes aren't backed up" to
                        (if (b.lastBackupAt == 0L) "No backup has been made yet." else "Last backup was ${TimeText.ago(b.lastBackupAt, now)}.") +
                        " Open PassVault and tap Back up now."
                else -> null
            }
        }

        fun sendTest(context: Context): Boolean = notify(
            context, ID_TEST,
            "PassVault reminders are on",
            "You'll get a nudge here if backups stop or you haven't typed your master password in a while.",
        )

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

        private fun notify(context: Context, id: Int, title: String, text: String): Boolean {
            if (!canNotify(context)) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Backup and master-password reminders"
                }
            )
            val open = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // contains no secrets
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            return try {
                NotificationManagerCompat.from(context).notify(id, notification)
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }
}
