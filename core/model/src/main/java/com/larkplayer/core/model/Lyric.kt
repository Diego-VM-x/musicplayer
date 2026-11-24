package com.larkplayer.core.model

/**
 * A karaoke token: a word or a syllable coming from an enhanced-LRC `<mm:ss.xx>`
 * mark or from an ID3 SYLT entry. `timestampMs` is the instant at which the
 * token starts being sung; -1 means "inherits from the previous token" and is
 * resolved by the parser before the model reaches the UI.
 */
data class LyricWord(
    val text: String,
    val timestampMs: Long = -1L,
)

/** A single lyric line. [timestampMs] is -1 for plain (unsynchronized) lyrics. */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    /** Start of the following line, or last word + short tail; -1 if unknown. */
    val endMs: Long = -1L,
    /** Word/syllable tokens. Empty for legacy flat lyrics. */
    val words: List<LyricWord> = emptyList(),
) {
    val hasWords: Boolean get() = words.isNotEmpty()
}

/** Parsed lyrics for a song. */
data class Lyrics(
    val songId: Long,
    val lines: List<LyricLine> = emptyList(),
    val plainText: String = "",
    /** `[offset:...]` from an LRC file, already applied to [lines]. */
    val offsetMs: Long = 0L,
    /** True only when the source really contained `<mm:ss.xx>` / SYLT word marks. */
    val hasWordTimestamps: Boolean = false,
) {
    /** True when there is something to show (synced lines or plain text). */
    val hasText: Boolean get() = lines.isNotEmpty() || plainText.isNotBlank()

    /** Synced lyrics have real timestamps; plain lyrics must scroll freely. */
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.timestampMs >= 0 }
}
