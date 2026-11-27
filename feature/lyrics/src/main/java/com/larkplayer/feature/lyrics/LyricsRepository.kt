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
            // [loadForSongId] already refreshes a plain cache with a timed
            // source when one exists, so its result is final here.
            if (cached.hasText) return@withContext cached
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
        // Do not return early for a timed cache: replacing the sibling .lrc
        // file must still take effect. resolve() compares timedScore and keeps
        // the cache when nothing better was found.
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
        }.getOrNull()
        if (song == null) {
            // Untimed embedded text is still valid lyrics: keep it plain so the
            // UI can show the "no timing" indicator instead of guessing times.
            return@withContext cached?.let { LrcParser.parse(id, it) } ?: Lyrics(0)
        }
        resolve(song, cached)
    }

    /** Explicit online lookup from the lyrics screen; always caches the result. */
    suspend fun searchOnline(
        title: String,
        artist: String,
        durationMs: Long,
        songId: Long,
    ): Lyrics? = withContext(Dispatchers.IO) {
        val text = LrclibClient.fetch(artist, title, durationMs)
            ?.let { LyricsTextUtils.clean(it) }
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        if (songId > 0) runCatching { songDao.updateLyrics(songId, text) }
        LrcParser.parse(songId, text)
    }

    /**
     * Embedded tags / local .lrc files, then cache the raw text in Room.
     *
     * The previous version generated fake timestamps from the audio energy of
     * untimed lyrics. That made the highlight drift (the onset detector is not
     * a singer) and hid the honest "unsynchronized" state, so it is no longer
     * applied here: plain lyrics stay plain and scroll freely.
     */
    private suspend fun resolve(song: Song, cachedOverride: String? = null): Lyrics {
        val cached = cachedOverride ?: if (song.id > 0) songDao.lyricsFor(song.id) else null
        val fresh = MediaFileUtils.findLyricsText(context, song)
            ?.let { LyricsTextUtils.clean(it) }
            ?.takeIf { it.isNotBlank() }

        val useFresh = fresh != null &&
            (cached.isNullOrBlank() || LyricsTextUtils.timedScore(fresh) > LyricsTextUtils.timedScore(cached))
        var chosen = if (useFresh) fresh else cached
        var fromInternet = false

        // Local .lrc/tags first. When they are missing or untimed, ask LRCLIB
        // (free, no API key). Network failures are silent: local lyrics still work.
        if (chosen == null || LyricsTextUtils.timedScore(chosen) == 0) {
            val remote = LrclibClient.fetch(song.artist, song.title, song.duration)
                ?.let { LyricsTextUtils.clean(it) }
                ?.takeIf { it.isNotBlank() }
            if (remote != null &&
                (chosen == null || LyricsTextUtils.timedScore(remote) > LyricsTextUtils.timedScore(chosen))
            ) {
                chosen = remote
                fromInternet = true
            }
        }
        val text = chosen ?: return Lyrics(song.id)

        val parsed = LrcParser.parse(song.id, text)
        if ((useFresh || fromInternet) && song.id > 0 && text != cached) {
            runCatching { songDao.updateLyrics(song.id, text) }
        }
        return parsed
    }
}
