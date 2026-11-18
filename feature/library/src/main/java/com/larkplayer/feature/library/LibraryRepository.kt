package com.larkplayer.feature.library

import com.larkplayer.core.common.Result
import com.larkplayer.core.database.dao.HistoryDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.HistoryEntity
import com.larkplayer.core.database.entity.SongEntity
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.scanner.MediaScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val songDao: SongDao,
    private val historyDao: HistoryDao,
    private val scanner: MediaScanner,
) {
    val songs: Flow<List<Song>> = songDao.observeAll().map { list -> list.map { it.toDomain() } }

    val recent: Flow<List<Song>> = historyDao.observeRecent(100).map { list ->
        list.mapNotNull { id -> /* resolved by caller if needed */ null }
    }

    suspend fun scan(): Result<Int> = try {
        Result.Success(scanner.scan())
    } catch (e: Exception) {
        Result.Error(e, "Scan failed")
    }

    suspend fun recordPlay(songId: Long) {
        historyDao.record(HistoryEntity(songId = songId))
    }

    fun search(list: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return list
        return list.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }

    fun groupByAlbum(list: List<Song>) = list.groupBy { it.album }
    fun groupByArtist(list: List<Song>) = list.groupBy { it.artist }

    private fun SongEntity.toDomain() = Song(
        id = id, title = title, artist = artist, album = album, albumId = albumId,
        duration = duration, path = path, isVideo = isVideo, dateAdded = dateAdded,
    )
}
