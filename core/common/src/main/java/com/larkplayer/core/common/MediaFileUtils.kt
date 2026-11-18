package com.larkplayer.core.common

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.larkplayer.core.model.Song
import timber.log.Timber

/** Helpers for MediaStore queries — replaces the original app's `media_util` package. */
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
        MediaStore.Audio.Media.MIME_TYPE,
    )

    /**
     * Scans all local audio files.
     * Important: IS_MUSIC alone hides valid .flac/.m4a/.opus files on many ROMs,
     * so we use a MIME_TYPE filter OR a positive duration instead.
     */
    fun queryAudio(context: Context): List<Song> {
        val result = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"
        val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        try {
            context.contentResolver.query(uri, AUDIO_PROJECTION, selection, null, sort)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIdx = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistIdx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumIdx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdIdx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durIdx = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val pathIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateIdx = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val sizeIdx = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                while (c.moveToNext()) {
                    val path = c.getString(pathIdx) ?: ""
                    if (path.isBlank()) continue
                    result += Song(
                        id = c.getLong(idIdx),
                        title = c.getString(titleIdx)?.takeIf { it.isNotBlank() }
                            ?: path.substringAfterLast('/').substringBeforeLast('.'),
                        artist = c.getString(artistIdx)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown artist",
                        album = c.getString(albumIdx)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown album",
                        albumId = c.getLong(albumIdIdx),
                        duration = c.getLong(durIdx),
                        path = path,
                        dateAdded = c.getLong(dateIdx),
                        size = c.getLong(sizeIdx),
                    )
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "MediaStore permission not granted")
        }
        return result.distinctBy { it.id }
    }

    /** Album art content URI for a given album id. */
    fun albumArtUri(albumId: Long): Uri =
        Uri.parse("content://media/external/audio/albumart/$albumId")

    /** Looks for a sibling .lrc file next to the audio file. */
    fun hasLocalLyrics(songPath: String): Boolean = runCatching {
        val file = java.io.File(songPath)
        listOf(
            java.io.File(file.parentFile, "${file.nameWithoutExtension}.lrc"),
            java.io.File(file.parentFile, "${file.nameWithoutExtension}.LRC"),
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "Lyrics/${file.nameWithoutExtension}.lrc"),
        ).any { it.exists() }
    }.getOrDefault(false)

    /** Looks for a sibling .lrc file next to the audio file. */
    fun findLocalLyrics(songPath: String): String? = runCatching {
        val file = java.io.File(songPath)
        val candidates = listOf(
            java.io.File(file.parentFile, "${file.nameWithoutExtension}.lrc"),
            java.io.File(file.parentFile, "${file.nameWithoutExtension}.LRC"),
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "Lyrics/${file.nameWithoutExtension}.lrc"),
        )
        candidates.firstOrNull { it.exists() }?.readText()
    }.getOrNull()

    /** MediaStore content URI for a song id. */
    fun songUri(song: Song): Uri = Uri.parse(song.contentUri)
}
