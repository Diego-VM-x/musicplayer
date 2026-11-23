package com.larkplayer.core.model

/** A single lyric line. [timestampMs] is -1 for plain (unsynchronized) lyrics. */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

/** Parsed lyrics for a song. */
data class Lyrics(
    val songId: Long,
    val lines: List<LyricLine> = emptyList(),
    val plainText: String = "",
    /** `[offset:...]` from an LRC file, already applied to [lines]. */
    val offsetMs: Long = 0L,
) {
    /** True when there is something to show (synced lines or plain text). */
    val hasText: Boolean get() = lines.isNotEmpty() || plainText.isNotBlank()

    /** Synced lyrics have real timestamps; plain lyrics must scroll freely. */
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.timestampMs >= 0 }
}
