package com.larkplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.larkplayer.core.database.entity.HistoryEntity
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import com.larkplayer.core.database.entity.PlaylistWithCover
import com.larkplayer.core.database.entity.QueueEntity
import com.larkplayer.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount,
            COALESCE(
                (SELECT s.albumId FROM playlist_songs ps
                 INNER JOIN songs s ON s.id = ps.songId
                 WHERE ps.playlistId = p.id ORDER BY ps.position LIMIT 1),
                -1
            ) AS coverAlbumId
        FROM playlists p
        ORDER BY p.updatedAt DESC
        """
    )
    fun observePlaylistsWithCovers(): Flow<List<PlaylistWithCover>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long = System.currentTimeMillis())

    /** Updates name and custom cover together (used by the playlist editor). */
    @Query("UPDATE playlists SET name = :name, coverUri = :coverUri, updatedAt = :now WHERE id = :id")
    suspend fun update(id: Long, name: String, coverUri: String?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.position")
    fun observeSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    fun observeAllPlaylistSongs(): Flow<List<PlaylistSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongs(refs: List<PlaylistSong>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun setPosition(playlistId: Long, songId: Long, position: Int)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId IN (:songIds)")
    suspend fun removeSongs(playlistId: Long, songIds: List<Long>)

    @Transaction
    suspend fun replaceSongs(playlistId: Long, refs: List<PlaylistSong>) {
        clearSongs(playlistId)
        addSongs(refs)
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearSongs(playlistId: Long)
}

/** Number of plays per song, used by the "Más reproducido" sort mode. */
data class PlayCount(val songId: Long, val count: Int)

/** Most played songs for the "Recuerdos musicales" recap. */
data class TopSong(
    @Embedded val song: SongEntity,
    val playCount: Int,
)

@Dao
interface HistoryDao {
    @Query("SELECT songId, COUNT(*) AS count FROM history GROUP BY songId")
    fun observePlayCounts(): Flow<List<PlayCount>>

    @Query(
        """
        SELECT s.*, COUNT(h.songId) AS playCount FROM history h
        INNER JOIN songs s ON s.id = h.songId
        GROUP BY h.songId ORDER BY playCount DESC LIMIT 5
        """
    )
    fun observeTopSongs(): Flow<List<TopSong>>

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>>

    @Insert
    suspend fun record(entry: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun observe(): Flow<List<QueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(items: List<QueueEntity>)

    @Query("DELETE FROM queue")
    suspend fun clear()
}
