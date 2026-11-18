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
    /** Loads a sibling .lrc file for the currently playing title. */
    suspend fun loadForTitle(title: String): Lyrics = withContext(Dispatchers.IO) {
        val song = MediaFileUtils.queryAudio(context).firstOrNull {
            it.title.equals(title, ignoreCase = true)
        } ?: return@withContext Lyrics(0)

        val raw = MediaFileUtils.findLocalLyrics(song.path)
        if (raw.isNullOrBlank()) Lyrics(song.id) else LrcParser.parse(song.id, raw)
    }
}
