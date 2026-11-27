package com.larkplayer.feature.backup

import android.content.Context
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Enqueues the real (previously unused) scheduled backup worker. */
object BackupScheduler {
    private const val UNIQUE_WORK = "lark_auto_backup"

    fun schedule(context: Context, uri: Uri, includeSongs: Boolean) {
        val input = workDataOf(
            BackupWorker.KEY_BACKUP_URI to uri.toString(),
            BackupWorker.KEY_INCLUDE_SONGS to includeSongs,
        )
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setInputData(input)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) =
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
}
