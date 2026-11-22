package com.larkplayer.feature.backup

import android.content.Context
import android.net.Uri
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class LpbkImportResult(
    val playlists: Int,
    val linked: Int,
    val unmatched: Int,
    val deviceModel: String?,
)

private data class LpbkMedia(
    val id: Int?,
    val location: String?,
    val rel: String?,
    val name: String?,
)

private data class LpbkMember(val mediaId: Int?, val order: Int)

private data class LpbkPlaylist(
    val name: String?,
    val createTime: Long,
    val cover: String?,
    val members: List<LpbkMember>,
)

/**
 * Reads the new local backup format introduced in Lark Player 2027.3.x.
 *
 * Layout: "LPBK" | version(uint16) | 14 bytes | IV(12) | metaLen(uint32 BE)
 *         meta JSON | AES-CTR payload | HMAC-SHA256(header+meta+payload)
 */
@Singleton
class LpbkImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
) {
    suspend fun import(uri: Uri): Result<LpbkImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "lark_local_import").apply { mkdirs() }
            val copy = File(dir, "backup.lpbk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("No se pudo leer la copia local")
            val bytes = copy.readBytes()
            val result = parse(bytes, dir)
            runCatching { copy.delete() }
            result
        }
    }

    private suspend fun parse(bytes: ByteArray, workDir: File): LpbkImportResult {
        require(bytes.size >= 24 + 32) { "Archivo LPBK truncado" }
        require(bytes.copyOfRange(0, 4).toString(StandardCharsets.US_ASCII) == "LPBK") { "No es una copia LPBK" }
        val version = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        require(version <= 1) { "Versión LPBK no soportada: $version" }
        val metaLen = ((bytes[20].toInt() and 0xFF) shl 24) or
            ((bytes[21].toInt() and 0xFF) shl 16) or
            ((bytes[22].toInt() and 0xFF) shl 8) or
            (bytes[23].toInt() and 0xFF)
        require(metaLen in 1..65536) { "Longitud de metadata inválida" }
        require(bytes.size > 24 + metaLen + 32) { "Archivo LPBK truncado" }

        val legacyKey = LEGACY_KEY.toByteArray(StandardCharsets.UTF_8)
        val encKey = SecretKeySpec(hmac(legacyKey, "lpbk-enc"), "AES")
        val macKey = hmac(legacyKey, "lpbk-mac")
        val signedLength = bytes.size - 32
        val expectedMac = hmac(macKey, bytes.copyOfRange(0, signedLength))
        val storedMac = bytes.copyOfRange(signedLength, bytes.size)
        require(expectedMac.contentEquals(storedMac)) { "Firma HMAC no válida" }

        val metaJson = bytes.copyOfRange(24, 24 + metaLen).toString(StandardCharsets.UTF_8)
        val meta = JSONObject(metaJson)
        val deviceModel = meta.optString("device_model").takeIf { it.isNotBlank() && it != "null" }

        val iv16 = ByteArray(16)
        System.arraycopy(bytes, 8, iv16, 0, 12)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encKey, IvParameterSpec(iv16))
        val payload = cipher.doFinal(bytes, 24 + metaLen, signedLength - (24 + metaLen))

        val head = payload.copyOfRange(0, 2).joinToString(" ") { "%02X".format(it) }
        if (payload.size < 2 || payload[0] != 0x50.toByte() || payload[1] != 0x4B.toByte()) {
            error("El payload descifrado no es ZIP (cabezal=$head, bytes=${payload.size})")
        }
        val payloadFile = File(workDir, "payload.zip")
        payloadFile.writeBytes(payload)
        val sections = readSections(payloadFile)
        val mediaById = sections.media.associateBy { it.id }
        val songs = songDao.observeAll().first()
        val byName = songs.associateBy { normalize(it.title) }
        val byPath = songs.associateBy { normalize(it.path) }

        var linked = 0
        var unmatched = 0
        var restored = 0
        sections.playlists.forEach { source ->
            val name = source.name?.takeIf { it.isNotBlank() } ?: return@forEach
            if (name.startsWith("INVALID_", true)) return@forEach
            val existing = playlistDao.observePlaylists().first()
                .firstOrNull { it.name.equals(name, true) }
            val coverUri = source.cover?.let { cover ->
                sections.covers[cover]?.let { bytes ->
                    runCatching {
                        val file = File(context.filesDir, "lark_cover_${System.currentTimeMillis()}.png")
                        file.writeBytes(bytes)
                        Uri.fromFile(file).toString()
                    }.getOrNull()
                }
            }
            val playlistId = existing?.id ?: playlistDao.insert(
                PlaylistEntity(
                    name = name,
                    createdAt = source.createTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    coverUri = coverUri,
                ),
            )
            val refs = buildList {
                source.members.sortedBy { it.order }.forEachIndexed { index, member ->
                    val media = mediaById[member.mediaId]
                    val candidate = listOfNotNull(
                        media?.name?.let { byName[normalize(it)] },
                        media?.name?.let { byPath[normalize(it)] },
                        media?.location?.let { byPath[normalize(it)] },
                        media?.rel?.let { byPath[normalize(it)] },
                    ).firstOrNull()
                    if (candidate != null) {
                        add(PlaylistSong(playlistId, candidate.id, index))
                        linked++
                    } else {
                        unmatched++
                    }
                }
            }
            playlistDao.replaceSongs(playlistId, refs)
            restored++
        }
        return LpbkImportResult(restored, linked, unmatched, deviceModel)
    }

    private fun readSections(payloadFile: File): Sections {
        val bytes = payloadFile.readBytes()
        var lastError: Throwable? = null
        // ZipFile uses the central directory; ZipInputStream is kept as a
        // fallback because ROM implementations differ on data descriptors.
        runCatching { return readZipFile(payloadFile) }
            .onFailure { lastError = it }
        runCatching { return readZipStream(bytes) }
            .onFailure { lastError = it }
        throw IllegalStateException(
            "No se pudo leer el ZIP interno: ${lastError?.message.orEmpty()}",
            lastError,
        )
    }

    private fun readZipFile(payloadFile: File): Sections {
        var media = emptyList<LpbkMedia>()
        var playlists = emptyList<LpbkPlaylist>()
        val covers = HashMap<String, ByteArray>()
        ZipFile(payloadFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                when {
                    entry.name.endsWith("media.json") -> media = parseMedia(
                        zip.getInputStream(entry).use { it.readBytes() },
                    )
                    entry.name.endsWith("playlists.json") -> playlists = parsePlaylists(
                        zip.getInputStream(entry).use { it.readBytes() },
                    )
                    entry.name.startsWith("files/") && !entry.isDirectory ->
                        covers[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }
        require(playlists.isNotEmpty()) { "ZIP sin playlists.json" }
        return Sections(media, playlists, covers)
    }

    private fun readZipStream(bytes: ByteArray): Sections {
        var media = emptyList<LpbkMedia>()
        var playlists = emptyList<LpbkPlaylist>()
        val covers = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name.endsWith("media.json") -> media = parseMedia(zip.readBytes())
                    entry.name.endsWith("playlists.json") -> playlists = parsePlaylists(zip.readBytes())
                    entry.name.startsWith("files/") && !entry.isDirectory ->
                        covers[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(playlists.isNotEmpty()) { "ZIP sin playlists.json" }
        return Sections(media, playlists, covers)
    }

    private fun parseMedia(bytes: ByteArray): List<LpbkMedia> =
        parseJsonMedia(JSONArray(bytes.toString(StandardCharsets.UTF_8)))

    private fun parsePlaylists(bytes: ByteArray): List<LpbkPlaylist> =
        parseJsonPlaylists(JSONArray(bytes.toString(StandardCharsets.UTF_8)))

    private fun parseJsonMedia(array: JSONArray?): List<LpbkMedia> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            LpbkMedia(
                id = o.optInt("id", -1).takeIf { it > 0 },
                location = o.optString("location").takeIf { it.isNotBlank() && it != "null" },
                rel = o.optString("rel").takeIf { it.isNotBlank() && it != "null" },
                name = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    private fun parseJsonPlaylists(array: JSONArray?): List<LpbkPlaylist> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val members = o.optJSONArray("members")
            LpbkPlaylist(
                name = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
                createTime = o.optLong("create_time", 0L),
                cover = o.optString("cover").takeIf { it.isNotBlank() && it != "null" },
                members = if (members == null) emptyList() else
                    (0 until members.length()).mapNotNull { j ->
                        val m = members.optJSONObject(j) ?: return@mapNotNull null
                        LpbkMember(
                            mediaId = m.optInt("media_id", -1).takeIf { it > 0 },
                            order = m.optInt("order", 0),
                        )
                    },
            )
        }
    }

    private data class Sections(
        val media: List<LpbkMedia>,
        val playlists: List<LpbkPlaylist>,
        val covers: Map<String, ByteArray>,
    )

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

    private fun normalize(value: String?): String = value.orEmpty()
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .trim()
        .lowercase()
        .replace(Regex("[\\u0300-\\u036f]"), "")

    private companion object {
        const val LEGACY_KEY = "Qvr1I4Zp10pJL2Zb3/XuwMTCk9m4b5gZ"
    }
}
