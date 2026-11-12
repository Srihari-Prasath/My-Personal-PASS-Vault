package com.personal.passvault.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.passvault.data.backup.BackupManager
import com.personal.passvault.data.backup.UploadResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerEntryPoint {
    fun backupManager(): BackupManager
}

/**
 * Uploads the already-encrypted pending backup. Runs only with network, retries with backoff,
 * and works while the vault is locked (it never touches the database or keys).
 */
class BackupUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors
            .fromApplication(applicationContext, BackupWorkerEntryPoint::class.java)
            .backupManager()
        return when (val result = manager.uploadPending()) {
            is UploadResult.Failed -> if (result.retryable && runAttemptCount < 8) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "passvault_backup_upload"

        /** REPLACE: several quick edits collapse into one upload of the newest backup. */
        fun enqueue(context: Context, delaySeconds: Long) {
            val request = OneTimeWorkRequestBuilder<BackupUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
