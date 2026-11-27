package com.larkplayer.core.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Periodic/point-in-time checks and APK verification through WorkManager. */
object UpdateScheduler {
    private const val CHECK = "lark-update-check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(CHECK)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CHECK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun checkNow(context: Context, manual: Boolean) {
        val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(CHECK)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (manual) "$CHECK-manual" else "$CHECK-now",
            if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun verify(context: Context, downloadId: Long) {
        val request = OneTimeWorkRequestBuilder<VerifyUpdateWorker>()
            .setInputData(workDataOf(VerifyUpdateWorker.KEY_DOWNLOAD_ID to downloadId))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "lark-update-verify-$downloadId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

class AppUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!UpdateManager.isInitialized()) UpdateManager.initFromPackage(applicationContext)
        // Wait for the network result: a failed check must be retried, not
        // silently reported as a successful no-update.
        if (UpdateManager.checkNowSuspend(manual = false)) Result.success() else Result.retry()
    }
}

class VerifyUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!UpdateManager.isInitialized()) UpdateManager.initFromPackage(applicationContext)
        val id = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) Result.failure()
        else when (UpdateManager.verifyDownload(id)) {
            VerifyOutcome.SUCCESS, VerifyOutcome.REJECTED -> Result.success()
            VerifyOutcome.RETRY -> Result.retry()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "lark_update_download_id"
    }
}
