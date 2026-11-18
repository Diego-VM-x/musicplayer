package com.larkplayer.core.model

/**
 * Domain model for a media item (audio or video).
 * Mirrors the song identity used by the original Lark MediaStore scanning.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String = "<unknown>",
    val album: String = "<unknown>",
    val albumId: Long = -1L,
    val duration: Long = 0L,
    val path: String = "",
    val isVideo: Boolean = false,
    val dateAdded: Long = 0L,
    val size: Long = 0L,
) {
    /** Content URI for Media3 / ExoPlayer. */
    val contentUri: String
        get() = "content://media/external/${if (isVideo) "video" else "audio"}/media/$id"
}
