package com.larkplayer.feature.backup

import com.google.gson.annotations.SerializedName
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Exact JSON model used by the original Lark Player Drive backup.
 * Sources: `jadx_full/sources/com/dywx/larkplayer/drive/server/PlaylistInfos.java`,
 * `DrivePlaylistBean.java` and `MediaItem.java`.
 */
data class PlaylistInfos(
    @SerializedName("playlistInfos") val playlistInfos: List<DrivePlaylistBean> = emptyList(),
)

data class DrivePlaylistBean(
    @SerializedName("playlist_name") val playlistName: String? = null,
    @SerializedName("playlist_id") val playlistId: String? = null,
    @SerializedName("createTime") val createTime: Long? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("items") val items: List<LarkMediaItem> = emptyList(),
)

data class LarkMediaItem(
    @SerializedName("songs_name") val songsName: String? = null,
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("play_count") val playCount: Long = 0L,
)

data class PlayCountInfos(
    @SerializedName("items") val items: List<LarkMediaItem> = emptyList(),
)

/** The original encrypts the .info files with this fixed AES key. */
object LarkBackupCrypto {
    private const val KEY = "Qvr1I4Zp10pJL2Zb3/XuwMTCk9m4b5gZ"

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), "AES"))
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)), Base64.DEFAULT)
    }

    /** Old unencrypted backups are plain JSON starting with '{'. */
    fun decrypt(value: String): String {
        val text = value.trim()
        if (text.startsWith("{") && text.endsWith("}")) return text
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), "AES"))
        return String(cipher.doFinal(Base64.decode(text, Base64.DEFAULT)), StandardCharsets.UTF_8)
    }
}

object OriginalBackupFiles {
    const val PLAYLISTS = "(Don’t delete) Playlists you backed up in Lark Player.info"
    const val PLAY_COUNT = "(Don’t delete) Play count you backed up in Lark Player.info"
    const val PLAYTIME = "(Don’t delete) Playtime you backed up in Lark Player.info"

    /** Matching is case-insensitive in the original app and handles curly/straight apostrophes. */
    fun isPlaylists(name: String): Boolean {
        val n = name.lowercase().replace("’", "'")
        return n.contains("playlists you backed up in lark player") ||
            (n.startsWith("(don't delete)") && n.contains("playlist"))
    }
    fun isPlayCount(name: String): Boolean {
        val n = name.lowercase().replace("’", "'")
        return n.contains("play count you backed up in lark player")
    }
}
