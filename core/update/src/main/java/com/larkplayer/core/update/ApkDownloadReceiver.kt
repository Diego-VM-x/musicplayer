package com.larkplayer.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.DownloadManager

/** Handles download completion without ever launching an activity from background. */
class ApkDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0L) return
        if (!UpdateManager.isInitialized()) UpdateManager.initFromPackage(context)
        when (AppUpdate.queryDownload(context, id)?.status) {
            DownloadManager.STATUS_SUCCESSFUL -> UpdateManager.onDownloadComplete(id)
            DownloadManager.STATUS_FAILED -> UpdateManager.onDownloadFailed(
                id,
                "La descarga falló. Puedes reintentarla desde Ajustes.",
            )
        }
    }
}
