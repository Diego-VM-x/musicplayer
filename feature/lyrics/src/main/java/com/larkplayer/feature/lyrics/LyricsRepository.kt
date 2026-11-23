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
        songDao.lyricsFor(id)?.takeIf { it.isNotBlank() }?.let {
            return@withContext LrcParser.parse(id, it)
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
        val raw = MediaFileUtils.findLyricsText(context, song)
            ?.let { LyricsTextUtils.clean(it) }
            ?.takeIf { it.isNotBlank() }
            ?: return Lyrics(song.id)
        if (song.id > 0 && songDao.lyricsFor(song.id).isNullOrBlank()) {
            runCatching { songDao.updateLyrics(song.id, raw) }
        }
        return LrcParser.parse(song.id, raw)
    }
}
