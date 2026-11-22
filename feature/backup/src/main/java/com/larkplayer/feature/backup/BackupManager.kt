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
import com.larkplayer.feature.library.scanner.OriginalLibraryScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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

data class UniversalImportResult(
    val message: String,
    val restoredPlaylists: Int = 0,
    val linkedSongs: Int = 0,
    val unmatchedSongs: Int = 0,
    val errors: List<String> = emptyList(),
)

private data class NormalizedMedia(
    val id: Int? = null,
    val title: String? = null,
    val fileName: String? = null,
    val path: String? = null,
)

private data class ParsedPlaylist(
    val name: String,
    val createdAt: Long,
    val coverUri: String?,
    val songReferences: List<NormalizedMedia>,
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao,
    private val queueDao: QueueDao,
    private val mediaScanner: MediaScanner,
    private val originalLibraryScanner: OriginalLibraryScanner,
    private val gson: Gson,
) {

    /**
     * Universal importer that automatically identifies the backup file format:
     * 1. LPBK binary (.lrc local backup of Lark Player 2027.x)
     * 2. AES-ECB encrypted Drive .info or plain JSON (.info / .json)
     * 3. Zip archive (.zip produced by LarkClean or Lark Player)
     * 4. SQLite database (vlc_database)
     */
    suspend fun importUniversal(uri: Uri): Result<UniversalImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val tempDir = File(context.filesDir, "import_temp").apply { mkdirs() }
            val tempFile = File(tempDir, "temp_backup_${System.currentTimeMillis()}.bin")

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("No se pudo leer el archivo seleccionado")

                val bytes = tempFile.readBytes()
                require(bytes.isNotEmpty()) { "El archivo está vacío" }

                // Check 1: Magic header "LPBK"
                if (bytes.size >= 4 && bytes[0] == 'L'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
                    bytes[2] == 'B'.code.toByte() && bytes[3] == 'K'.code.toByte()
                ) {
                    return@runCatching importLpbkBytes(bytes, tempDir)
                }

                // Check 2: Zip signature "PK" (0x50, 0x4B)
                if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                    return@runCatching importZipFile(tempFile)
                }

                // Check 3: SQLite signature "SQLite format 3"
                val sqliteHeader = "SQLite format 3\u0000"
                if (bytes.size >= 16 && bytes.copyOfRange(0, 16).toString(StandardCharsets.US_ASCII) == sqliteHeader) {
                    val dbResult = originalLibraryScanner.importFromSaf(uri).getOrThrow()
                    return@runCatching UniversalImportResult(
                        message = "Importadas ${dbResult.playlists} playlists desde la base de datos de Lark",
                        restoredPlaylists = dbResult.playlists,
                        linkedSongs = dbResult.linked,
                        unmatchedSongs = dbResult.unmatched,
                    )
                }

                // Check 4: Text-based (.info encrypted with AES or plain JSON)
                val text = bytes.toString(StandardCharsets.UTF_8).trim()
                val decryptedJson = runCatching { LarkBackupCrypto.decrypt(text) }.getOrNull() ?: text
                if (decryptedJson.contains("playlistInfos") || decryptedJson.startsWith("{")) {
                    val driveInfos = runCatching { gson.fromJson(decryptedJson, PlaylistInfos::class.java) }.getOrNull()
                    if (driveInfos != null && driveInfos.playlistInfos.isNotEmpty()) {
                        return@runCatching mergeDrivePlaylists(driveInfos, emptyMap())
                    }
                }

                error("Formato de respaldo no reconocido o archivo dañado")
            } finally {
                runCatching { tempFile.delete() }
            }
        }
    }

    private suspend fun importLpbkBytes(bytes: ByteArray, workDir: File): UniversalImportResult {
        require(bytes.size >= 24 + 32) { "Archivo LPBK truncado" }
        val version = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        require(version <= 1) { "Versión LPBK no soportada: $version" }
        val metaLen = ((bytes[20].toInt() and 0xFF) shl 24) or
            ((bytes[21].toInt() and 0xFF) shl 16) or
            ((bytes[22].toInt() and 0xFF) shl 8) or
            (bytes[23].toInt() and 0xFF)
        require(metaLen in 1..65536) { "Metadatos LPBK inválidos" }
        require(bytes.size > 24 + metaLen + 32) { "Archivo LPBK truncado" }

        val legacyKey = LEGACY_KEY.toByteArray(StandardCharsets.UTF_8)
        val encKey = SecretKeySpec(hmac(legacyKey, "lpbk-enc"), "AES")
        val macKey = hmac(legacyKey, "lpbk-mac")
        val signedLength = bytes.size - 32
        val expectedMac = hmac(macKey, bytes.copyOfRange(0, signedLength))
        val storedMac = bytes.copyOfRange(signedLength, bytes.size)
        require(expectedMac.contentEquals(storedMac)) { "Firma HMAC no válida en copia local" }

        val iv16 = ByteArray(16)
        System.arraycopy(bytes, 8, iv16, 0, 12)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encKey, IvParameterSpec(iv16))
        val payload = cipher.doFinal(bytes, 24 + metaLen, signedLength - (24 + metaLen))

        if (payload.size < 2 || payload[0] != 0x50.toByte() || payload[1] != 0x4B.toByte()) {
            error("El contenido descifrado de la copia no es válido")
        }

        val payloadFile = File(workDir, "payload.zip")
        payloadFile.writeBytes(payload)
        return try {
            val parsed = readLpbkZip(payloadFile)
            storePlaylistsWithoutDuplicates(parsed, "Copia local de Lark Player")
        } finally {
            runCatching { payloadFile.delete() }
        }
    }

    private fun readLpbkZip(payloadFile: File): List<ParsedPlaylist> {
        // Intento 1: ZipFile (lee el directorio central, tolera data descriptors normales)
        runCatching {
            return readLpbkZipFile(payloadFile)
        }.onFailure { e ->
            Timber.w(e, "ZipFile falló para LPBK payload, intentando ZipInputStream")
        }
        // Intento 2: ZipInputStream (stream secuencial, tolera otros formatos de data descriptor)
        return readLpbkZipStream(payloadFile.readBytes())
    }

    private fun readLpbkZipFile(payloadFile: File): List<ParsedPlaylist> {
        var mediaJson = ""
        var playlistsJson = ""
        val covers = HashMap<String, ByteArray>()

        ZipFile(payloadFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                when {
                    entry.name.endsWith("media.json") ->
                        mediaJson = zip.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                    entry.name.endsWith("playlists.json") ->
                        playlistsJson = zip.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                    entry.name.startsWith("files/") && !entry.isDirectory ->
                        covers[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }

        require(playlistsJson.isNotBlank()) { "La copia no contiene playlists.json" }
        return parseLpbkSections(mediaJson, playlistsJson, covers)
    }

    private fun readLpbkZipStream(bytes: ByteArray): List<ParsedPlaylist> {
        var mediaJson = ""
        var playlistsJson = ""
        val covers = HashMap<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name.endsWith("media.json") ->
                        mediaJson = zip.readBytes().toString(StandardCharsets.UTF_8)
                    entry.name.endsWith("playlists.json") ->
                        playlistsJson = zip.readBytes().toString(StandardCharsets.UTF_8)
                    entry.name.startsWith("files/") && !entry.isDirectory ->
                        covers[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        require(playlistsJson.isNotBlank()) { "La copia no contiene playlists.json (stream)" }
        return parseLpbkSections(mediaJson, playlistsJson, covers)
    }

    private fun parseLpbkSections(
        mediaJson: String,
        playlistsJson: String,
        covers: Map<String, ByteArray>,
    ): List<ParsedPlaylist> {
        val mediaArray = if (mediaJson.isNotBlank()) JSONArray(mediaJson) else JSONArray()
        val mediaMap = mutableMapOf<Int, NormalizedMedia>()
        for (i in 0 until mediaArray.length()) {
            val o = mediaArray.optJSONObject(i) ?: continue
            val id = o.optInt("id", -1).takeIf { it > 0 } ?: continue
            mediaMap[id] = NormalizedMedia(
                id = id,
                title = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
                fileName = o.optString("rel").takeIf { it.isNotBlank() && it != "null" }
                    ?: o.optString("name").takeIf { it.isNotBlank() && it != "null" },
                path = o.optString("location").takeIf { it.isNotBlank() && it != "null" },
            )
        }

        val playlistsArray = JSONArray(playlistsJson)
        val result = mutableListOf<ParsedPlaylist>()

        for (i in 0 until playlistsArray.length()) {
            val o = playlistsArray.optJSONObject(i) ?: continue
            val name = o.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: continue
            if (name.startsWith("INVALID_", true)) continue

            val createTime = o.optLong("create_time", System.currentTimeMillis())
            val coverKey = o.optString("cover").takeIf { it.isNotBlank() && it != "null" }
            val coverUri = coverKey?.let { key ->
                covers[key]?.let { coverBytes ->
                    runCatching {
                        val file = File(context.filesDir, "lark_cover_${System.currentTimeMillis()}_$i.png")
                        file.writeBytes(coverBytes)
                        Uri.fromFile(file).toString()
                    }.getOrNull()
                }
            }

            val membersArray = o.optJSONArray("members")
            val songRefs = mutableListOf<NormalizedMedia>()
            if (membersArray != null) {
                val memberList = mutableListOf<Pair<Int, Int>>()
                for (j in 0 until membersArray.length()) {
                    val m = membersArray.optJSONObject(j) ?: continue
                    val mId = m.optInt("media_id", -1)
                    val order = m.optInt("order", j)
                    if (mId > 0) memberList.add(order to mId)
                }
                memberList.sortBy { it.first }
                memberList.forEach { (_, mId) ->
                    val media = mediaMap[mId] ?: NormalizedMedia(id = mId)
                    songRefs.add(media)
                }
            }

            result.add(ParsedPlaylist(name, createTime, coverUri, songRefs))
        }

        return result
    }

    private suspend fun importZipFile(zipFile: File): UniversalImportResult {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.length < 500) {
                    entries[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }

        // Caso 1: Archivo propio LarkClean "backup.json"
        val ownJson = entries["backup.json"]?.toString(Charsets.UTF_8)
        if (ownJson != null) {
            val data = runCatching { gson.fromJson(ownJson, BackupData::class.java) }.getOrNull()
            if (data != null) {
                val songs = songDao.observeAll().first()
                val byId = songs.associateBy { it.id }

                val parsedPlaylists = data.playlists.map { pl ->
                    val refs = (data.playlistSongs.filter { it.playlistId == pl.id })
                        .sortedBy { it.position }
                        .mapNotNull { ref ->
                            val s = byId[ref.songId]
                            if (s != null) {
                                NormalizedMedia(title = s.title, fileName = File(s.path).name, path = s.path)
                            } else null
                        }
                    ParsedPlaylist(pl.name, pl.createdAt, pl.coverUri, refs)
                }
                return storePlaylistsWithoutDuplicates(parsedPlaylists, "Copia de respaldo LarkClean")
            }
        }

        // Caso 2: Archivo .info de Drive dentro del ZIP
        val originalEntry = entries.entries.firstOrNull { OriginalBackupFiles.isPlaylists(it.key) }
        if (originalEntry != null) {
            val json = LarkBackupCrypto.decrypt(originalEntry.value.toString(Charsets.UTF_8))
            val infos = runCatching { gson.fromJson(json, PlaylistInfos::class.java) }.getOrNull()
            if (infos != null) {
                return mergeDrivePlaylists(infos, entries)
            }
        }

        error("El archivo ZIP no contiene playlists válidas de Lark Player")
    }

    private suspend fun mergeDrivePlaylists(
        infos: PlaylistInfos,
        zipEntries: Map<String, ByteArray>,
    ): UniversalImportResult {
        var copiedFiles = 0
        val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "larkplayer/restore")
            .apply { mkdirs() }

        // If audio files are bundled in ZIP, unpack them
        if (zipEntries.isNotEmpty()) {
            zipEntries.forEach { (name, bytes) ->
                if (name.startsWith("Music/") && !name.endsWith("/")) {
                    val fName = name.substringAfter("Music/")
                    if (fName.isNotBlank()) {
                        runCatching {
                            File(musicDir, fName).writeBytes(bytes)
                            copiedFiles++
                        }
                    }
                }
            }
            if (copiedFiles > 0) {
                mediaScanner.scan()
            }
        }

        val parsed = infos.playlistInfos.mapNotNull { bean ->
            val name = bean.playlistName.orEmpty()
            if (name.isBlank() || name.startsWith("INVALID_", true)) return@mapNotNull null
            val refs = bean.items.map { item ->
                val fileName = item.songsName?.substringAfterLast('/')
                NormalizedMedia(title = fileName, fileName = fileName)
            }
            ParsedPlaylist(
                name = name,
                createdAt = bean.createTime ?: System.currentTimeMillis(),
                coverUri = bean.cover?.takeIf { it.isNotBlank() },
                songReferences = refs,
            )
        }

        val res = storePlaylistsWithoutDuplicates(parsed, "Respaldo Google Drive")
        return if (copiedFiles > 0) {
            res.copy(message = "${res.message} ($copiedFiles canciones de audio restauradas)")
        } else res
    }

    /**
     * Stores playlists cleanly without duplicates:
     * - If playlist exists by name (case-insensitive), updates it and replaces songs.
     * - Matches local songs flexibly: path -> filename -> title (ignoring accents & extensions).
     * - Guarantees accurate order and zero duplicate song entries.
     */
    private suspend fun storePlaylistsWithoutDuplicates(
        playlists: List<ParsedPlaylist>,
        sourceDescription: String,
    ): UniversalImportResult {
        if (playlists.isEmpty()) error("No se encontraron playlists para importar")

        val allSongs = songDao.observeAll().first()
        val byPath = allSongs.associateBy { normalize(it.path) }
        val byFileName = allSongs.associateBy { normalize(File(it.path).name) }
        val byTitle = allSongs.associateBy { normalize(it.title) }

        var totalLinked = 0
        var totalUnmatched = 0
        var restoredCount = 0
        val notices = mutableListOf<String>()

        val existingPlaylists = playlistDao.observePlaylists().first()

        playlists.forEach { source ->
            val cleanName = source.name.trim()
            if (cleanName.isBlank()) return@forEach

            val existing = existingPlaylists.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
            val playlistId = existing?.id ?: playlistDao.insert(
                PlaylistEntity(
                    name = cleanName,
                    createdAt = source.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    coverUri = source.coverUri,
                ),
            )

            val refs = mutableListOf<PlaylistSong>()
            val addedSongIds = mutableSetOf<Long>() // Prevent duplicate song IDs in the same playlist

            source.songReferences.forEach { ref ->
                val matchedSong = listOfNotNull(
                    ref.path?.let { byPath[normalize(it)] },
                    ref.fileName?.let { byFileName[normalize(it)] },
                    ref.fileName?.let { byPath[normalize(it)] },
                    ref.title?.let { byTitle[normalize(it)] },
                    ref.title?.let { byFileName[normalize(it)] },
                ).firstOrNull()

                if (matchedSong != null) {
                    if (addedSongIds.add(matchedSong.id)) {
                        refs.add(PlaylistSong(playlistId, matchedSong.id, refs.size))
                        totalLinked++
                    }
                } else {
                    totalUnmatched++
                }
            }

            // Atómicamente limpiamos y reemplazamos las canciones de esta playlist para evitar duplicados
            playlistDao.replaceSongs(playlistId, refs)
            restoredCount++
        }

        if (totalUnmatched > 0) {
            notices.add("$totalUnmatched canciones no se encontraron en el dispositivo (puedes agregarlas luego).")
        }

        return UniversalImportResult(
            message = "Importadas $restoredCount playlists con $totalLinked canciones vinculadas ($sourceDescription)",
            restoredPlaylists = restoredCount,
            linkedSongs = totalLinked,
            unmatchedSongs = totalUnmatched,
            errors = notices,
        )
    }

    suspend fun export(uri: Uri, includeSongs: Boolean): Result<UniversalImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = songDao.observeAll().first()
            val playlists = playlistDao.observePlaylists().first()
            val playlistSongs = playlistDao.observeAllPlaylistSongs().first()
            val history = historyDao.observeRecent(Int.MAX_VALUE).first()
            val queue = queueDao.observe().first()
            val playCounts = historyDao.observePlayCounts().first().associate { it.songId to it.count }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    // 1. Lossless format
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

                    // 2. Original-compatible format
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

                    var copied = 0
                    if (includeSongs) {
                        songs.forEach { song ->
                            runCatching {
                                val songObj = Song(
                                    id = song.id,
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    duration = song.duration,
                                    path = song.path,
                                )
                                val sUri = MediaFileUtils.songUri(songObj)
                                context.contentResolver.openInputStream(sUri)?.use { input ->
                                    zip.putNextEntry(ZipEntry("Music/${File(song.path).name}"))
                                    input.copyTo(zip)
                                    zip.closeEntry()
                                    copied++
                                }
                            }
                        }
                    }

                    UniversalImportResult(
                        message = "Copia exportada: ${playlists.size} playlists" +
                            if (includeSongs) ", $copied canciones incluidas" else " (sin canciones)",
                        restoredPlaylists = playlists.size,
                        linkedSongs = songs.size,
                    )
                }
            } ?: error("No se pudo crear el archivo de destino")
        }
    }

    private fun normalize(value: String?): String = value.orEmpty()
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .trim()
        .lowercase()
        .replace(Regex("[\\u0300-\\u036f]"), "")

    private fun hmac(key: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance("HMACSHA256")
        mac.init(SecretKeySpec(key, "HMACSHA256"))
        return mac.doFinal(label.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HMACSHA256")
        mac.init(SecretKeySpec(key, "HMACSHA256"))
        return mac.doFinal(data)
    }

    private companion object {
        const val LEGACY_KEY = "Qvr1I4Zp10pJL2Zb3/XuwMTCk9m4b5gZ"
    }
}
