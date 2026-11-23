package com.larkplayer.core.common

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.content.ContentUris
import android.os.Environment
import com.larkplayer.core.model.Song
import timber.log.Timber
import java.io.File

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

    /**
     * Finds an .lrc file through MediaStore. Direct File access to the audio
     * folder is blocked by scoped storage on Android 13+, so the sibling-file
     * check alone used to fail there.
     */
    fun findLyricsText(context: Context, song: Song): String? = runCatching {
        val audioFile = java.io.File(song.path)
        val base = audioFile.nameWithoutExtension
        val stems = listOf(
            base,
            song.title,
            "${song.artist} - ${song.title}",
            "${song.title} - ${song.artist}",
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dirs = listOfNotNull(
            audioFile.parentFile,
            File(musicDir, "Lyrics"),
            File(Environment.getExternalStorageDirectory(), "Lyrics"),
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Lyrics",
            ),
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Lyrics",
            ),
            musicDir,
        )

        // Direct files still work on many devices (Android 10 and older, or files
        // owned by this app).
        for (dir in dirs) {
            stems.forEach { stem ->
                listOf("$stem.lrc", "$stem.LRC").forEach { name ->
                    val file = File(dir, name)
                    if (file.exists()) return file.readText()
                }
            }
            runCatching { dir.listFiles() }.getOrNull()
                ?.firstOrNull { file ->
                    file.name.endsWith(".lrc", ignoreCase = true) &&
                        stems.any { file.nameWithoutExtension.equals(it, ignoreCase = true) }
                }?.let { return it.readText() }
        }

        // Scoped storage: search MediaStore by file name. Use a generic LIKE
        // with the stem only (not "%stem.lrc"): many ROMs index the name
        // without extension or with extra suffixes, and escaped characters
        // such as brackets/quotes otherwise break the query.
        val collection = MediaStore.Files.getContentUri("external")
        val candidates = mutableListOf<Pair<Long, String>>()
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, OpenableColumns.DISPLAY_NAME),
                "LOWER(${OpenableColumns.DISPLAY_NAME}) LIKE LOWER(?)",
                arrayOf("%.lrc"),
                null,
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else ""
                    candidates += c.getLong(idIdx) to name
                }
            }
        }
        // First exact stem match, then fuzzy match ignoring case/accents.
        val normalizedStems = stems.map { normalizeLyricName(it) }
        val ranked = candidates.sortedBy { rankOfLyric(it.second, normalizedStems) }
        for ((id, name) in ranked) {
            if (rankOfLyric(name, normalizedStems) > 1) break
            val uri = ContentUris.withAppendedId(collection, id)
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!text.isNullOrBlank()) return text
        }
        null
    }.getOrNull() ?: findLocalLyrics(song.path)

    private fun normalizeLyricName(value: String): String = value
        .lowercase()
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun rankOfLyric(name: String, stems: List<String>): Int {
        val n = normalizeLyricName(name.substringBeforeLast('.'))
        return when {
            stems.any { it.equals(n, ignoreCase = true) } -> 0
            stems.any { n.contains(it, ignoreCase = true) || it.contains(n, ignoreCase = true) } -> 1
            else -> 2
        }
    }

    fun hasLocalLyrics(context: Context, song: Song): Boolean =
        findLyricsText(context, song)?.isNotBlank() == true

    /** MediaStore content URI for a song id. */
    fun songUri(song: Song): Uri = Uri.parse(song.contentUri)
}
