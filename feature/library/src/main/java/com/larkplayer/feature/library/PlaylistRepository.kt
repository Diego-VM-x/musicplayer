package com.larkplayer.feature.library

import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import com.larkplayer.core.database.entity.PlaylistWithCover
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
) {
    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()
    val playlistsWithCovers: Flow<List<PlaylistWithCover>> = dao.observePlaylistsWithCovers()

    suspend fun create(name: String): Long = dao.insert(PlaylistEntity(name = name))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun addSong(playlistId: Long, songId: Long) {
        dao.addSongs(listOf(PlaylistSong(playlistId, songId, 0)))
    }

    suspend fun setSongs(playlistId: Long, songIds: List<Long>) {
        dao.replaceSongs(playlistId, songIds.mapIndexed { i, id -> PlaylistSong(playlistId, id, i) })
    }

    suspend fun removeSong(playlistId: Long, songId: Long) = dao.removeSong(playlistId, songId)

    suspend fun songsOnce(playlistId: Long) = dao.observeSongs(playlistId).first()

    fun songs(playlistId: Long): Flow<List<com.larkplayer.core.database.entity.SongEntity>> =
        dao.observeSongs(playlistId)
}
