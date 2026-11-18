package com.larkplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.larkplayer.core.database.dao.HistoryDao
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.QueueDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.HistoryEntity
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import com.larkplayer.core.database.entity.QueueEntity
import com.larkplayer.core.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSong::class,
        HistoryEntity::class,
        QueueEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LarkDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
}
