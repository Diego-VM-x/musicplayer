package com.larkplayer.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val path: String,
    val isVideo: Boolean = false,
    val dateAdded: Long = 0L,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Custom cover picked by the user (SAF content uri), null = first song's art. */
    val coverUri: String? = null,
)

/** Projection used by the Playlists tab (cover art + song count). */
data class PlaylistWithCover(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int,
    val coverAlbumId: Long,
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val songId: Long,
    val position: Int,
)
