package com.larkplayer.feature.lyrics

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.model.Lyrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Loads the sibling .lrc for the playing song (MediaStore + direct file). */
    suspend fun loadForTitle(
        title: String,
        artist: String = "",
        path: String = "",
    ): Lyrics = withContext(Dispatchers.IO) {
        val library = MediaFileUtils.queryAudio(context)
        val byPath = path.takeIf { it.isNotBlank() }
            ?.let { p -> library.firstOrNull { it.path.equals(p, ignoreCase = true) } }
        val song = byPath ?: library.firstOrNull {
            it.title.equals(title, ignoreCase = true) &&
                (artist.isBlank() || it.artist.equals(artist, ignoreCase = true))
        } ?: library.firstOrNull { it.title.equals(title, ignoreCase = true) }
            ?: return@withContext Lyrics(0)

        val raw = MediaFileUtils.findLyricsText(context, song)
        if (raw.isNullOrBlank()) Lyrics(song.id) else LrcParser.parse(song.id, raw)
    }

    /** Most reliable path: resolve the exact MediaStore row by song id. */
    suspend fun loadForSongId(id: Long): Lyrics = withContext(Dispatchers.IO) {
        if (id <= 0) return@withContext Lyrics(0)
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
                if (!c.moveToFirst()) null else com.larkplayer.core.model.Song(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    artist = c.getString(2).orEmpty(),
                    path = c.getString(3).orEmpty(),
                )
            }
        }.getOrNull() ?: return@withContext Lyrics(0)

        val raw = MediaFileUtils.findLyricsText(context, song)
        if (raw.isNullOrBlank()) Lyrics(song.id) else LrcParser.parse(song.id, raw)
    }
}
