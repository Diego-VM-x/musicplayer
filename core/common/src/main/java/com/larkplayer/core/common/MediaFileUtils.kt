package com.larkplayer.core.common

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
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

    private val LYRIC_EXTENSIONS = listOf("lrc", "LRC", "txt", "TXT")

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
                val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
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

    /** Looks for a sibling .lrc / .txt file next to the audio file. */
    fun hasLocalLyrics(songPath: String): Boolean = runCatching {
        val file = File(songPath)
        lyricStems(file.nameWithoutExtension).any { stem ->
            lyricDirs(file.parentFile).any { dir ->
                LYRIC_EXTENSIONS.any { ext -> File(dir, "$stem.$ext").isFile }
            }
        }
    }.getOrDefault(false)

    /** Reads a sibling .lrc / .txt file next to the audio file (BOM-safe). */
    fun findLocalLyrics(songPath: String): String? = findLocalLyrics(songPath, emptyArray())

    private fun findLocalLyrics(
        songPath: String,
        extraStems: Array<String>,
        extensions: List<String> = LYRIC_EXTENSIONS,
    ): String? {
        if (songPath.isBlank()) return null
        val audio = File(songPath)
        val stems = (listOf(audio.nameWithoutExtension) + extraStems)
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val dirs = lyricDirs(audio.parentFile)
        val lowerExts = extensions.map { it.lowercase() }.toSet()

        // Exact names first, then a case/accent-insensitive match in the folder.
        for (stem in stems) {
            for (dir in dirs) {
                for (ext in extensions) {
                    LyricsTextUtils.readFile(File(dir, "$stem.$ext"))?.let { return it }
                }
            }
        }
        for (dir in dirs) {
            runCatching { dir.listFiles() }.getOrNull()
                ?.firstOrNull { file ->
                    file.isFile && file.extension.lowercase() in lowerExts &&
                        stems.any { normalizeLyricName(it) == normalizeLyricName(file.nameWithoutExtension) }
                }?.let { LyricsTextUtils.readFile(it) }?.let { return it }
        }
        return null
    }

    private fun lyricDirs(parent: File?): List<File> = listOfNotNull(
        parent,
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lyrics"),
        File(Environment.getExternalStorageDirectory(), "Lyrics"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lyrics"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lyrics"),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
    )

    private fun lyricStems(base: String): List<String> = listOf(base)

    /**
     * Priority: (1) tags embedded in the audio file, (2) local .lrc/.txt files,
     * (3) MediaStore-indexed lyric files. Works with scoped storage and with
     * UTF-8 / UTF-16 / ISO-8859-1 files.
     */
    fun findLyricsText(context: Context, song: Song): String? {
        val title = song.title.trim()
        val artist = song.artist.trim()
        val stems = listOf(
            File(song.path).nameWithoutExtension,
            title,
            "$artist - $title",
            "$title - $artist",
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val embedded = EmbeddedLyrics.read(context, song)?.takeIf { it.isNotBlank() }
        val local = findLocalLyrics(song.path, stems.toTypedArray())

        // Real timestamps win: a plain USLT/.txt can only be distributed
        // proportionally and made the highlight drift from the actual singing.
        listOf(
            findLocalLyrics(song.path, stems.toTypedArray(), listOf("lrc", "LRC")),
            embedded,
        ).firstOrNull { it != null && LyricsTextUtils.hasTimestamps(it) }?.let { return it }

        // Scoped storage: search MediaStore by file name. A generic LIKE on the
        // extension avoids escaping problems with brackets/quotes in song names.
        val collection = MediaStore.Files.getContentUri("external")
        val nameColumn = OpenableColumns.DISPLAY_NAME
        val candidates = mutableListOf<Pair<Long, String>>()
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, nameColumn),
                "LOWER($nameColumn) LIKE ? OR LOWER($nameColumn) LIKE ?",
                arrayOf("%.lrc", "%.txt"),
                null,
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = c.getColumnIndex(nameColumn)
                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else ""
                    candidates += c.getLong(idIdx) to name
                }
            }
        }
        val normalizedStems = stems.map { normalizeLyricName(it) }
        val ranked = candidates.sortedBy { rankOfLyric(it.second, normalizedStems) }
        var timedLrc: String? = null
        var anyLyrics: String? = null
        for ((id, name) in ranked) {
            if (rankOfLyric(name, normalizedStems) > 1) break
            if (name.substringAfterLast('.').lowercase() !in setOf("lrc", "txt")) continue
            val uri = ContentUris.withAppendedId(collection, id)
            val text = LyricsTextUtils.readUri(context, uri) ?: continue
            if (name.substringAfterLast('.').equals("lrc", ignoreCase = true) &&
                LyricsTextUtils.hasTimestamps(text) && timedLrc == null
            ) timedLrc = text
            if (anyLyrics == null) anyLyrics = text
        }
        if (timedLrc != null) return timedLrc

        // Last resort: unsynchronized text, in the old priority order.
        return local ?: embedded ?: anyLyrics
    }

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
