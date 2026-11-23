package com.larkplayer.feature.library

import com.larkplayer.core.database.dao.HistoryDao
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import com.larkplayer.core.database.entity.PlaylistWithCover
import com.larkplayer.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    private val historyDao: HistoryDao,
) {
    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()
    val playlistsWithCovers: Flow<List<PlaylistWithCover>> = dao.observePlaylistsWithCovers()

    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

    /** Play counts keyed by song id, used by the "Más reproducido" sort mode. */
    fun playCounts(): Flow<Map<Long, Int>> = historyDao.observePlayCounts().map { counts ->
        counts.associate { it.songId to it.count }
    }

    fun songs(playlistId: Long): Flow<List<SongEntity>> = dao.observeSongs(playlistId)

    suspend fun songsOnce(playlistId: Long): List<SongEntity> = songs(playlistId).first()

    /** New playlists go to the end of the manual order. */
    suspend fun create(name: String): Long = dao.insert(
        PlaylistEntity(name = name, position = dao.maxPlaylistPosition() + 1),
    )

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    /** Saves both the name and the custom cover picked in the editor. */
    suspend fun update(id: Long, name: String, coverUri: String?) = dao.update(id, name, coverUri)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun addSong(playlistId: Long, songId: Long) = addSongs(playlistId, listOf(songId))

    /** Appends songs at the end, skipping duplicates and ids already present. */
    suspend fun addSongs(playlistId: Long, songIds: List<Long>) {
        val existing = songsOnce(playlistId).map { it.id }.toSet()
        var position = dao.maxPosition(playlistId) + 1
        val refs = buildList {
            songIds.distinct().forEach { id ->
                if (id !in existing) {
                    add(PlaylistSong(playlistId, id, position))
                    position++
                }
            }
        }
        if (refs.isNotEmpty()) dao.addSongs(refs)
    }

    suspend fun removeSong(playlistId: Long, songId: Long) = dao.removeSong(playlistId, songId)

    suspend fun removeSongs(playlistId: Long, songIds: List<Long>) {
        if (songIds.isNotEmpty()) dao.removeSongs(playlistId, songIds)
    }

    /** Persists a manual ordering: positions become 0..n-1. */
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        dao.replaceSongs(
            playlistId,
            orderedSongIds.mapIndexed { index, id -> PlaylistSong(playlistId, id, index) },
        )
    }

    suspend fun setSongs(playlistId: Long, songIds: List<Long>) = reorder(playlistId, songIds)

    /** Persists the order of the playlists themselves (Playlists tab, manual mode). */
    suspend fun reorderPlaylists(orderedIds: List<Long>) = dao.reorderPlaylists(orderedIds)
}
