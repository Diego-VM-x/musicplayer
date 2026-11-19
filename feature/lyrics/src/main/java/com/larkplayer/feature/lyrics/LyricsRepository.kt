package com.larkplayer.feature.lyrics

import android.content.Context
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
}
