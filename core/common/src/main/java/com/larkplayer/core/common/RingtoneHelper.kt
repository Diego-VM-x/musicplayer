package com.larkplayer.core.common

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import com.larkplayer.core.model.Song
import timber.log.Timber

/** Sets a local song as the device ringtone (Lark Player feature). */
object RingtoneHelper {
    fun set(context: Context, song: Song): Boolean = runCatching {
        val uri: Uri = MediaFileUtils.songUri(song)
        if (Settings.System.canWrite(context)) {
            RingtoneManager.setActualDefaultRingtoneUri(
                context, RingtoneManager.TYPE_RINGTONE, uri
            )
            true
        } else {
            Timber.w("WRITE_SETTINGS permission not granted for ringtone")
            false
        }
    }.getOrElse {
        Timber.e(it, "Failed to set ringtone")
        false
    }
}
