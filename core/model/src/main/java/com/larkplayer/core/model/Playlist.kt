package com.larkplayer.core.model

/** A user playlist containing an ordered list of song IDs. */
data class Playlist(
    val id: Long = 0L,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
