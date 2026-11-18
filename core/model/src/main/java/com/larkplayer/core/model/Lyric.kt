package com.larkplayer.core.model

/** A single synchronized lyric line parsed from LRC. */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

/** Parsed lyrics for a song. */
data class Lyrics(
    val songId: Long,
    val lines: List<LyricLine> = emptyList(),
    val plainText: String = "",
)
