package com.larkplayer.core.database.di

import android.content.Context
import androidx.room.Room
import com.larkplayer.core.database.LarkDatabase
import com.larkplayer.core.database.dao.HistoryDao
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.QueueDao
import com.larkplayer.core.database.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LarkDatabase =
        Room.databaseBuilder(context, LarkDatabase::class.java, "lark.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSongDao(db: LarkDatabase): SongDao = db.songDao()
    @Provides fun providePlaylistDao(db: LarkDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideHistoryDao(db: LarkDatabase): HistoryDao = db.historyDao()
    @Provides fun provideQueueDao(db: LarkDatabase): QueueDao = db.queueDao()
}
