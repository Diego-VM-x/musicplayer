package com.larkplayer.feature.library.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.database.dao.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Scans the device MediaStore and syncs results into Room. */
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
) {
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        val fresh = MediaFileUtils.queryAudioOrNull(context)
        val merged = LibrarySync.merge(songDao.observeAllOnce(), fresh.orEmpty(), fresh != null)
        if (!merged.shouldReplace) return@withContext songDao.observeAllOnce().size

        songDao.replaceAll(merged.songs)
        merged.songs.size
    }
}
