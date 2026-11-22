package com.larkplayer.feature.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.Gson
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.database.dao.HistoryDao
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.QueueDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.HistoryEntity
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import com.larkplayer.core.database.entity.QueueEntity
import com.larkplayer.core.database.entity.SongEntity
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.scanner.MediaScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupData(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val includeSongs: Boolean = false,
    val songs: List<SongEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistSongs: List<PlaylistSong> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val queue: List<QueueEntity> = emptyList(),
)

data class BackupResult(val message: String, val restoredPlaylists: Int = 0, val restoredSongs: Int = 0)

/**
 * Local ZIP backup that can both read and write the original Lark Player
 * playlist format (AES-encrypted `Playlists ... .info` files).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao,
    private val queueDao: QueueDao,
    private val mediaScanner: MediaScanner,
    private val gson: Gson,
) {
    suspend fun export(uri: Uri, includeSongs: Boolean): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = songDao.observeAll().first()
            val playlists = playlistDao.observePlaylists().first()
            val playlistSongs = playlistDao.observeAllPlaylistSongs().first()
            val history = historyDao.observeRecent(Int.MAX_VALUE).first()
            val queue = queueDao.observe().first()
            val playCounts = historyDao.observePlayCounts().first().associate { it.songId to it.count }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    // Our own lossless format.
                    zip.putNextEntry(ZipEntry("backup.json"))
                    zip.write(
                        gson.toJson(
                            BackupData(
                                includeSongs = includeSongs,
                                songs = songs,
                                playlists = playlists,
                                playlistSongs = playlistSongs,
                                history = history,
                                queue = queue,
                            ),
                        ).toByteArray(Charsets.UTF_8),
                    )
                    zip.closeEntry()

                    // Original-compatible format (encrypted with the app's key).
                    val byId = songs.associateBy { it.id }
                    val items = playlistSongs.groupBy { it.playlistId }
                    val originalPlaylists = playlists.map { playlist ->
                        DrivePlaylistBean(
                            playlistName = playlist.name,
                            playlistId = if (playlist.name.equals("Favoritos", true)) {
                                "playlist_id_favorite"
                            } else playlist.id.toString(),
                            createTime = playlist.createdAt,
                            cover = playlist.coverUri.orEmpty(),
                            items = (items[playlist.id].orEmpty())
                                .sortedBy { it.position }
                                .mapNotNull { ref ->
                                    val song = byId[ref.songId] ?: return@mapNotNull null
                                    LarkMediaItem(
                                        songsName = File(song.path).name,
                                        size = runCatching { File(song.path).length() }.getOrDefault(0L),
                                        playCount = (playCounts[song.id] ?: 0).toLong(),
                                    )
                                },
                        )
                    }
                    zip.putNextEntry(ZipEntry(OriginalBackupFiles.PLAYLISTS))
                    zip.write(LarkBackupCrypto.encrypt(gson.toJson(PlaylistInfos(originalPlaylists))).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    val countItems = songs.map { song ->
                        LarkMediaItem(
                            songsName = File(song.path).name,
                            size = runCatching { File(song.path).length() }.getOrDefault(0L),
                            playCount = (playCounts[song.id] ?: 0).toLong(),
                        )
                    }
                    zip.putNextEntry(ZipEntry(OriginalBackupFiles.PLAY_COUNT))
                    zip.write(LarkBackupCrypto.encrypt(gson.toJson(PlayCountInfos(countItems))).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    // Optional audio copy: same file names the original uses.
                    var copied = 0
                    if (includeSongs) {
                        songs.forEach { song ->
                            runCatching {
                                val uri = MediaFileUtils.songUri(
                                    Song(
                                        id = song.id,
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album,
                                        duration = song.duration,
                                        path = song.path,
                                    ),
                                )
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    zip.putNextEntry(ZipEntry("Music/${File(song.path).name}"))
                                    input.copyTo(zip)
                                    zip.closeEntry()
                                    copied++
                                }
                            }
                        }
                    }
                    BackupResult(
                        message = "Copia exportada: ${playlists.size} playlists" +
                            if (includeSongs) ", $copied canciones incluidas" else " (sin canciones)",
                    )
                }
            } ?: error("No se pudo abrir el archivo de destino")
        }
    }

    suspend fun import(uri: Uri): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = LinkedHashMap<String, ByteArray>()
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.length < 500) {
                            entries[entry.name] = zip.readBytes()
                        }
                        entry = zip.nextEntry
                    }
                }
            } ?: error("No se pudo abrir el ZIP")

            var restoredPlaylists = 0
            var restoredSongs = 0
            var format = "LarkClean"

            val ownJson = entries["backup.json"]?.toString(Charsets.UTF_8)
            if (ownJson != null) {
                val data = runCatching { gson.fromJson(ownJson, BackupData::class.java) }.getOrNull()
                if (data != null) {
                    val existingIds = songDao.observeAll().first().map { it.id }.toSet()
                    songDao.insertAll(data.songs.filter { it.id !in existingIds })
                    data.playlists.forEach { playlist ->
                        playlistDao.insert(playlist.copy(id = 0L))
                    }
                    val fresh = playlistDao.observePlaylists().first()
                    val byName = fresh.associateBy { it.name }
                    data.playlistSongs.groupBy { it.playlistId }.forEach { (oldId, refs) ->
                        val source = data.playlists.firstOrNull { it.id == oldId } ?: return@forEach
                        val target = byName[source.name] ?: return@forEach
                        playlistDao.replaceSongs(
                            target.id,
                            refs.map { it.copy(playlistId = target.id) },
                        )
                    }
                    restoredPlaylists = data.playlists.size
                    restoredSongs = data.songs.size
                }
            }

            // Original .info files (inside the ZIP, encrypted or plain JSON).
            val originalEntry = entries.entries.firstOrNull { OriginalBackupFiles.isPlaylists(it.key) }
            if (originalEntry != null) {
                format = "Lark Player original"
                val json = LarkBackupCrypto.decrypt(originalEntry.value.toString(Charsets.UTF_8))
                val infos = runCatching { gson.fromJson(json, PlaylistInfos::class.java) }.getOrNull()
                if (infos != null) {
                    restoredSongs = maxOf(restoredSongs, mergeOriginal(infos, entries))
                    restoredPlaylists = maxOf(restoredPlaylists, infos.playlistInfos.size)
                }
            }

            if (restoredPlaylists == 0 && restoredSongs == 0) error("El archivo no contiene playlists compatibles")
            BackupResult(
                message = "Restauradas $restoredPlaylists playlists y $restoredSongs canciones ($format)",
                restoredPlaylists = restoredPlaylists,
                restoredSongs = restoredSongs,
            )
        }
    }

    private suspend fun mergeOriginal(
        infos: PlaylistInfos,
        entries: Map<String, ByteArray>,
    ): Int {
        var copiedFiles = 0
        val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "larkplayer/restore")
            .apply { mkdirs() }
        infos.playlistInfos.forEach { bean ->
            val name = bean.playlistName.orEmpty()
            if (name.isBlank()) return@forEach
            val existing = playlistDao.observePlaylists().first().firstOrNull { it.name.equals(name, true) }
            val playlistId = existing?.id ?: playlistDao.insert(
                PlaylistEntity(
                    name = name,
                    createdAt = bean.createTime ?: System.currentTimeMillis(),
                    coverUri = bean.cover?.takeIf { it.isNotBlank() },
                ),
            )
            // mergeTo() semantics: replace this playlist's links, keep the rest.
            playlistDao.clearSongs(playlistId)
            bean.items.forEachIndexed { index, item ->
                val fileName = item.songsName?.substringAfterLast('/') ?: return@forEachIndexed
                var song = songDao.observeAll().first().firstOrNull { File(it.path).name == fileName }
                if (song == null) {
                    val bytes = entries["Music/$fileName"]
                    if (bytes != null) {
                        runCatching {
                            val target = File(musicDir, fileName)
                            target.writeBytes(bytes)
                            mediaScanner.scan()
                            copiedFiles++
                        }.onFailure { Timber.e(it, "No se pudo restaurar $fileName") }
                        song = songDao.observeAll().first().firstOrNull { File(it.path).name == fileName }
                    }
                }
                if (song != null) {
                    playlistDao.addSongs(listOf(PlaylistSong(playlistId, song.id, index)))
                }
            }
        }
        return copiedFiles
    }

    /** Imports a single `.info` / `.json` file exported by the original app. */
    suspend fun importOriginalInfo(uri: Uri): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No se pudo leer el archivo")
            val json = LarkBackupCrypto.decrypt(bytes.toString(Charsets.UTF_8))
            val infos = runCatching { gson.fromJson(json, PlaylistInfos::class.java) }.getOrNull()
                ?: error("El archivo no contiene playlists de Lark Player")
            val linked = mergeOriginal(infos, emptyMap())
            BackupResult(
                message = "Importadas ${infos.playlistInfos.size} playlists de Lark Player " +
                    "($linked canciones vinculadas automáticamente)",
                restoredPlaylists = infos.playlistInfos.size,
                restoredSongs = linked,
            )
        }
    }

    /** Finds original `.info` files the user may have copied to shared storage. */
    fun findOriginalInfoFiles(): List<Uri> {
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(null),
            File(context.filesDir, "backups").apply { mkdirs() },
            File("/storage/emulated/0/Android/data/com.dywx.larkplayer/files").takeIf { it.exists() },
        )
        val candidates = roots.flatMap { root ->
            runCatching {
                root.walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile }
                    .filter { file ->
                        val n = file.name.lowercase()
                        (n.contains("playlists you backed up in lark player") && (n.endsWith(".info") || n.endsWith(".json"))) ||
                            (n.contains("playlist") && (n.endsWith(".info") || n.endsWith(".json")))
                    }
                    .toList()
            }.getOrDefault(emptyList())
        }
        return candidates.distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .map { Uri.fromFile(it) }
    }

    /** Finds a backup in Downloads or the app's backup folder for one-tap restore. */
    fun findAutoBackup(): Uri? {
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(context.filesDir, "backups").apply { mkdirs() },
        )
        return dirs.flatMap { dir ->
            runCatching {
                dir.listFiles()?.filter { it.isFile && it.extension.equals("zip", true) }.orEmpty()
            }.getOrDefault(emptyList())
        }.maxByOrNull { it.lastModified() }?.let { Uri.fromFile(it) }
    }
}
