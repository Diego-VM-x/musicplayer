package com.larkplayer.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** v2 adds the optional playlist cover; playlists from v1 are preserved. */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN coverUri TEXT")
        }
    }

    /** v3 adds embedded lyrics stored with the song. */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN lyricsText TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LarkDatabase =
        Room.databaseBuilder(context, LarkDatabase::class.java, "lark.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSongDao(db: LarkDatabase): SongDao = db.songDao()
    @Provides fun providePlaylistDao(db: LarkDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideHistoryDao(db: LarkDatabase): HistoryDao = db.historyDao()
    @Provides fun provideQueueDao(db: LarkDatabase): QueueDao = db.queueDao()
}
