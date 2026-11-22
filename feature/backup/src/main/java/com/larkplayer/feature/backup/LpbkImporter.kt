package com.larkplayer.feature.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
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

private data class LpbkMeta(
    @SerializedName("format_version") val formatVersion: Int? = null,
    @SerializedName("app_version_code") val appVersionCode: Long = 0L,
    @SerializedName("exported_at") val exportedAt: Long? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("song_count") val songCount: Int = 0,
    @SerializedName("playlist_count") val playlistCount: Int = 0,
    @SerializedName("payload_size") val payloadSize: Long = 0L,
)

private data class LpbkMedia(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("rel") val rel: String? = null,
    @SerializedName("volume") val volume: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("length") val length: Long = 0L,
)

private data class LpbkMember(
    @SerializedName("media_id") val mediaId: Int? = null,
    @SerializedName("order") val order: Int = 0,
)

private data class LpbkPlaylist(
    @SerializedName("name") val name: String? = null,
    @SerializedName("create_time") val createTime: Long = 0L,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("members") val members: List<LpbkMember>? = null,
)

/**
 * Reads the new local backup format introduced in Lark Player 2027.3.x.
 *
 * File layout (reverse engineered from `feature/backup/pack`):
 *   "LPBK" | version(uint16 BE) | 14 bytes | 12-byte IV | 4-byte metaLen(BE)
 *   meta JSON (metaLen bytes) | AES-CTR payload | HMAC-SHA256(header+meta+payload, 32 bytes)
 *
 * Keys are derived with HMAC-SHA256 from the legacy Drive key and the labels
 * "lpbk-enc" / "lpbk-mac". The decrypted payload is a ZIP with section JSON files.
 */
@Singleton
class LpbkImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val gson: Gson,
) {
    suspend fun import(uri: Uri): Result<LpbkImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "lark_local_import").apply { mkdirs() }
            val copy = File(dir, "backup.lpbk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("No se pudo leer la copia local")
            val bytes = copy.readBytes()
            val result = parse(bytes)
            runCatching { copy.delete() }
            result
        }
    }

    private suspend fun parse(bytes: ByteArray): LpbkImportResult {
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
        val meta = runCatching { gson.fromJson(metaJson, LpbkMeta::class.java) }.getOrNull()

        val iv12 = bytes.copyOfRange(8, 20)
        val iv16 = ByteArray(16)
        System.arraycopy(iv12, 0, iv16, 0, 12)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encKey, IvParameterSpec(iv16))
        val payload = cipher.doFinal(bytes, 24 + metaLen, signedLength - (24 + metaLen))

        val sections = readSections(payload)
        val mediaById = sections.media.associateBy { it.id }
        val songs = songDao.observeAll().first()
        val byName = songs.associateBy { normalize(it.title) }
        val byPath = songs.associateBy { normalize(it.path) }

        var linked = 0
        var unmatched = 0
        sections.playlists.forEach { source ->
            val name = source.name?.takeIf { it.isNotBlank() } ?: return@forEach
            // Virtual playlists (favourites/recent) are excluded by the original
            // importer too (n55.C()).
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
                source.members.orEmpty().sortedBy { it.order }.forEachIndexed { index, member ->
                    val media = mediaById[member.mediaId]
                    val candidate = listOfNotNull(
                        media?.name?.let { byName[normalize(it)] },
                        media?.name?.let { n -> byPath[normalize(n)] },
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
        }
        return LpbkImportResult(
            playlists = sections.playlists.size,
            linked = linked,
            unmatched = unmatched,
            deviceModel = meta?.deviceModel,
        )
    }

    private fun readSections(payload: ByteArray): Sections {
        var media = emptyList<LpbkMedia>()
        var playlists = emptyList<LpbkPlaylist>()
        val covers = HashMap<String, ByteArray>()
        val isZip = payload.size >= 2 && payload[0] == 0x50.toByte() && payload[1] == 0x4B.toByte()
        if (isZip) {
            ZipInputStream(ByteArrayInputStream(payload)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name.endsWith("media.json") -> media = gson.fromJson(
                            zip.readBytes().toString(StandardCharsets.UTF_8),
                            Array<LpbkMedia>::class.java,
                        ).orEmpty().toList()
                        entry.name.endsWith("playlists.json") -> playlists = gson.fromJson(
                            zip.readBytes().toString(StandardCharsets.UTF_8),
                            Array<LpbkPlaylist>::class.java,
                        ).orEmpty().toList()
                        entry.name.startsWith("files/") && !entry.isDirectory ->
                            covers[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } else {
            val json = payload.toString(StandardCharsets.UTF_8)
            runCatching {
                val wrapper = gson.fromJson(json, LpbkSectionsJson::class.java)
                media = wrapper.media.orEmpty()
                playlists = wrapper.playlists.orEmpty()
            }
        }
        return Sections(media, playlists, covers)
    }

    private data class Sections(
        val media: List<LpbkMedia>,
        val playlists: List<LpbkPlaylist>,
        val covers: Map<String, ByteArray>,
    )

    private data class LpbkSectionsJson(
        @SerializedName("media") val media: List<LpbkMedia>? = null,
        @SerializedName("playlists") val playlists: List<LpbkPlaylist>? = null,
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
