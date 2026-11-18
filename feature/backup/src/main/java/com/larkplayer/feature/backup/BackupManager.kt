package com.larkplayer.feature.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.larkplayer.core.database.dao.HistoryDao
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.QueueDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.HistoryEntity
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import com.larkplayer.core.database.entity.QueueEntity
import com.larkplayer.core.database.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val songs: List<SongEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistSongs: List<PlaylistSong> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val queue: List<QueueEntity> = emptyList(),
)

/** Local ZIP backup — replaces the original app's Google Drive-only backup. */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao,
    private val queueDao: QueueDao,
    private val gson: Gson,
) {
    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = BackupData(
                songs = songDao.observeAll().first(),
                playlists = playlistDao.observePlaylists().first(),
                history = historyDao.observeRecent(Int.MAX_VALUE).first(),
                queue = queueDao.observe().first(),
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("backup.json"))
                    zip.write(gson.toJson(data).toByteArray())
                    zip.closeEntry()
                }
            } ?: error("Cannot open output stream")
        }
    }

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "backup.json") {
                            return@use BufferedReader(InputStreamReader(zip)).readText()
                        }
                        entry = zip.nextEntry
                    }
                    error("backup.json not found in archive")
                }
            } ?: error("Cannot open input stream")

            val data = gson.fromJson(json, BackupData::class.java)
            require(data.version == 1) { "Unsupported backup version" }

            songDao.clear()
            songDao.insertAll(data.songs)
            // Restore playlists and their song links.
            data.playlists.forEach { p -> playlistDao.insert(p) }
            data.playlistSongs.groupBy { it.playlistId }.forEach { (pid, refs) ->
                playlistDao.replaceSongs(pid, refs)
            }
        }
    }
}
