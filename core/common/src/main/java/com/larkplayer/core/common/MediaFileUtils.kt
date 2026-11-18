package com.larkplayer.core.common

import android.content.Context
import android.provider.MediaStore
import com.larkplayer.core.model.Song
import timber.log.Timber

/** Helpers for MediaStore queries — replaces the original `media_util` package. */
object MediaFileUtils {

    private val AUDIO_PROJECTION = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.SIZE,
    )

    /** Scans all local audio files. The original app scanned both audio and video. */
    fun queryAudio(context: Context): List<Song> {
        val result = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            context.contentResolver.query(uri, AUDIO_PROJECTION, selection, null, null)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                while (c.moveToNext()) {
                    result += Song(
                        id = c.getLong(idIdx),
                        title = c.getString(titleIdx) ?: "<unknown>",
                        artist = c.getString(artistIdx) ?: "<unknown>",
                        album = c.getString(albumIdx) ?: "<unknown>",
                        albumId = c.getLong(albumIdIdx),
                        duration = c.getLong(durIdx),
                        path = c.getString(pathIdx) ?: "",
                        dateAdded = c.getLong(dateIdx),
                        size = c.getLong(sizeIdx),
                    )
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "MediaStore permission not granted")
        }
        return result
    }

    /** Album art content URI for a given album id. */
    fun albumArtUri(albumId: Long): String =
        "content://media/external/audio/albumart/$albumId"
}
