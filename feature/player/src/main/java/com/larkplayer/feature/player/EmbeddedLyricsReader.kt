package com.larkplayer.feature.player

import android.content.Context
import com.larkplayer.core.common.EmbeddedLyrics

/**
 * Facade kept for the player feature.
 *
 * The old implementation duplicated the ID3 parser here and had two bugs:
 * it opened `file://` URIs through ContentResolver (unsupported on Android)
 * and used `InputStream.readNBytes` (API 33+, crashes on older devices).
 * All formats are now handled by the single shared reader in `core:common`.
 */
object EmbeddedLyricsReader {
    fun read(context: Context, songPath: String, songId: Long): String? =
        EmbeddedLyrics.read(context, songPath, songId)
}
