package com.larkplayer.feature.backup

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Scheduled backup worker (WorkManager). A destination URI must be supplied in
 * the input data — WorkManager runs after the activity is gone, so it cannot
 * ask the user for a SAF destination at that point.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_BACKUP_URI)
        if (uriString.isNullOrBlank()) {
            Timber.w("BackupWorker: no destination URI supplied")
            return Result.failure()
        }
        val includeSongs = inputData.getBoolean(KEY_INCLUDE_SONGS, false)
        val exported = runCatching {
            backupManager.export(Uri.parse(uriString), includeSongs)
        }.getOrNull() ?: return Result.retry()

        return if (exported.isSuccess) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_BACKUP_URI = "backup_uri"
        const val KEY_INCLUDE_SONGS = "include_songs"
    }
}
