package com.larkplayer.feature.library.scanner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.MediaStore
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class OriginalImportResult(
    val playlists: Int,
    val linked: Int,
    val unmatched: Int,
)

private data class OriginalEntry(
    val location: String?,
    val fileName: String?,
    val title: String?,
    val position: Int,
)

private data class OriginalPlaylist(
    val name: String,
    val createdAt: Long,
    val cover: String?,
    val entries: MutableList<OriginalEntry> = mutableListOf(),
)

/**
 * Reads a copy of the original app's private `vlc_database` and recreates its
 * playlists in our Room database. Songs are matched by path, then file name,
 * then title against the library already scanned from MediaStore.
 *
 * The original has no exported ContentProvider, so the user must supply the
 * database file through SAF (e.g. from a file manager with Android/data access
 * or `adb pull` on rooted devices).
 */
@Singleton
class OriginalLibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
) {
    suspend fun importFromSaf(dbUri: Uri): Result<OriginalImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "original_import").apply { mkdirs() }
            val copy = File(dir, "vlc_database.copy")
            context.contentResolver.openInputStream(dbUri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("No se pudo leer la base de datos seleccionada")

            val sql = """
                SELECT p.name, p.create_time, p.playlist_cover, p.playlist_order,
                       pm.playlist_order AS pos, pm.media_location,
                       m.title, m.file_name
                FROM playlist_table p
                LEFT JOIN playlist_media_table pm ON pm.playlist_name = p.name
                LEFT JOIN media_table m ON m._id = pm.media_location
                ORDER BY p.playlist_order, pm.playlist_order
            """.trimIndent()

            val byName = LinkedHashMap<String, OriginalPlaylist>()
            val query = runCatching {
                SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery(sql, null).use { c -> readRows(c, byName) }
                }
            }.getOrElse {
                // Older databases may not have playlist_order.
                val fallback = sql.replace("p.playlist_order,", "0 AS playlist_order,")
                    .replace("ORDER BY p.playlist_order, pm.playlist_order", "ORDER BY pm.playlist_order")
                SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery(fallback, null).use { c -> readRows(c, byName) }
                }
            }
            if (query == 0) error("No se encontraron playlists en vlc_database")

            val songs = songDao.observeAll().first()
            val byPath = songs.associateBy { normalize(it.path) }
            val byFileName = songs.associateBy { normalize(File(it.path).name) }
            val byTitle = songs.associateBy { normalize(it.title) }

            var linked = 0
            var unmatched = 0
            byName.values.forEach { source ->
                val existing = playlistDao.observePlaylists().first()
                    .firstOrNull { it.name.equals(source.name, true) }
                val playlistId = existing?.id ?: playlistDao.insert(
                    PlaylistEntity(
                        name = source.name,
                        createdAt = source.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        coverUri = source.cover?.takeIf { it.isNotBlank() },
                    ),
                )
                val refs = buildList {
                    source.entries.sortedBy { it.position }.forEachIndexed { index, entry ->
                        val path = resolvePath(entry.location)
                        val song = path?.let { byPath[normalize(it)] }
                            ?: byFileName[normalize(entry.fileName ?: path?.let(::File)?.name)]
                            ?: byTitle[normalize(entry.title)]
                        if (song != null) {
                            add(PlaylistSong(playlistId, song.id, index))
                            linked++
                        } else {
                            unmatched++
                        }
                    }
                }
                playlistDao.replaceSongs(playlistId, refs)
            }

            runCatching { copy.delete() }
            OriginalImportResult(byName.size, linked, unmatched)
        }.onFailure { Timber.e(it, "Failed to import vlc_database") }
    }

    private fun readRows(
        cursor: android.database.Cursor,
        target: LinkedHashMap<String, OriginalPlaylist>,
    ): Int {
        val nameIdx = cursor.getColumnIndex("name")
        val createdIdx = cursor.getColumnIndex("create_time")
        val coverIdx = cursor.getColumnIndex("playlist_cover")
        val posIdx = cursor.getColumnIndex("pos")
        val locIdx = cursor.getColumnIndex("media_location")
        val titleIdx = cursor.getColumnIndex("title")
        val fileIdx = cursor.getColumnIndex("file_name")
        if (nameIdx < 0) error("Formato de vlc_database no reconocido")

        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIdx).orEmpty()
            if (name.isBlank() || name.startsWith("INVALID_")) continue
            val playlist = target.getOrPut(name) {
                OriginalPlaylist(
                    name = name,
                    createdAt = if (createdIdx >= 0) cursor.getLong(createdIdx) else 0L,
                    cover = if (coverIdx >= 0) cursor.getString(coverIdx) else null,
                )
            }
            val location = if (locIdx >= 0) cursor.getString(locIdx) else null
            if (location.isNullOrBlank()) continue
            playlist.entries += OriginalEntry(
                location = location,
                fileName = if (fileIdx >= 0) cursor.getString(fileIdx) else null,
                title = if (titleIdx >= 0) cursor.getString(titleIdx) else null,
                position = if (posIdx >= 0) cursor.getInt(posIdx) else playlist.entries.size,
            )
        }
        return target.size
    }

    private fun resolvePath(location: String?): String? = runCatching {
        when {
            location?.startsWith("file:", true) == true -> Uri.parse(location).path
            location?.startsWith("content:", true) == true -> {
                val id = location.substringAfterLast('/')
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media.DATA),
                    "_id = ?",
                    arrayOf(id),
                    null,
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
            else -> null // online entries have no local file
        }
    }.getOrNull()

    private fun normalize(value: String?): String = value.orEmpty()
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .trim()
        .lowercase()
        .replace(Regex("[\\u0300-\\u036f]"), "")
}
