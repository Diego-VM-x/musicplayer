package com.larkplayer.feature.lyrics

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.larkplayer.core.common.LyricsTextUtils
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.model.Lyrics
import com.larkplayer.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
) {
    /** Loads lyrics for a song identified by title/artist/path (DB first). */
    suspend fun loadForTitle(
        title: String,
        artist: String = "",
        path: String = "",
    ): Lyrics = withContext(Dispatchers.IO) {
        val localMatch = songDao.observeAllOnce().firstOrNull { song ->
            (path.isNotBlank() && song.path.equals(path, ignoreCase = true)) ||
                (song.title.equals(title, ignoreCase = true) &&
                    (artist.isBlank() || song.artist.equals(artist, ignoreCase = true)))
        }
        if (localMatch != null && localMatch.id > 0) {
            val cached = loadForSongId(localMatch.id)
            // Plain cached text cannot follow the audio; keep looking for a
            // synchronized source (SYLT / .lrc) before accepting it.
            if (cached.hasText && LyricsTextUtils.hasTimestamps(cached.plainText)) {
                return@withContext cached
            }
        }

        val library = MediaFileUtils.queryAudio(context)
        val byPath = path.takeIf { it.isNotBlank() }
            ?.let { p -> library.firstOrNull { it.path.equals(p, ignoreCase = true) } }
        val song = byPath ?: library.firstOrNull {
            it.title.equals(title, ignoreCase = true) &&
                (artist.isBlank() || it.artist.equals(artist, ignoreCase = true))
        } ?: library.firstOrNull { it.title.equals(title, ignoreCase = true) }
            ?: return@withContext Lyrics(0)

        resolve(song)
    }

    /** Most reliable path: resolve the exact MediaStore row by song id. */
    suspend fun loadForSongId(id: Long): Lyrics = withContext(Dispatchers.IO) {
        if (id <= 0) return@withContext Lyrics(0)
        val cached = songDao.lyricsFor(id)
        if (!cached.isNullOrBlank() && LyricsTextUtils.hasTimestamps(cached)) {
            return@withContext LrcParser.parse(id, cached)
        }
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val song = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                ),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) null else Song(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    artist = c.getString(2).orEmpty(),
                    path = c.getString(3).orEmpty(),
                )
            }
        }.getOrNull() ?: return@withContext Lyrics(0)
        resolve(song)
    }

    /** Embedded tags → local/MediaStore files, then cache the raw text in Room. */
    private suspend fun resolve(song: Song): Lyrics {
        val cached = if (song.id > 0) songDao.lyricsFor(song.id) else null
        if (!cached.isNullOrBlank() && LyricsTextUtils.hasTimestamps(cached)) {
            return LrcParser.parse(song.id, cached)
        }

        val raw = MediaFileUtils.findLyricsText(context, song)
            ?.let { LyricsTextUtils.clean(it) }
            ?.takeIf { it.isNotBlank() }
            ?: return Lyrics(song.id)

        var parsed = LrcParser.parse(song.id, raw)
        var toCache = raw

        // No timestamps in the file: derive them once from the song's own
        // energy envelope instead of asking the user to adjust anything.
        if (!parsed.isSynced && parsed.lines.isNotEmpty() && song.path.isNotBlank()) {
            val duration = LyricsAutoSync.durationMs(song.path)
            val aligned = runCatching {
                LyricsAutoSync.align(song.path, parsed.lines, duration)
            }.getOrNull()
            if (!aligned.isNullOrEmpty()) {
                toCache = LyricsAutoSync.toLrc(aligned)
                parsed = LrcParser.parse(song.id, toCache)
            }
        }

        if (song.id > 0 && (cached.isNullOrBlank() || toCache != cached)) {
            runCatching { songDao.updateLyrics(song.id, toCache) }
        }
        return parsed
    }
}
