package com.larkplayer.feature.library.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.SongEntity
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
        val songs = MediaFileUtils.queryAudio(context).map { it.toEntity() }
        songDao.clear()
        songDao.insertAll(songs)
        songs.size
    }

    private fun com.larkplayer.core.model.Song.toEntity() = SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        path = path,
        isVideo = isVideo,
        dateAdded = dateAdded,
    )
}
