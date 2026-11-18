package com.larkplayer.feature.lyrics

import com.larkplayer.core.model.LyricLine
import com.larkplayer.core.model.Lyrics

/** Parser for standard LRC files: [mm:ss.xx]text */
object LrcParser {
    private val LINE_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\](.*)""")

    fun parse(songId: Long, raw: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            LINE_REGEX.findAll(line).forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: return@forEach
                val sec = m.groupValues[2].toLongOrNull() ?: return@forEach
                val frac = m.groupValues[3]
                val millis = when {
                    frac.isBlank() -> 0L
                    frac.length == 2 -> (frac.toLongOrNull() ?: 0L) * 10
                    frac.length == 3 -> frac.toLongOrNull() ?: 0L
                    else -> (frac.toLongOrNull() ?: 0L) * 10
                }
                val text = m.groupValues[4].trim()
                lines += LyricLine(min * 60_000 + sec * 1_000 + millis, text)
            }
        }
        return Lyrics(songId, lines.sortedBy { it.timestampMs }, raw)
    }

    /** Index of the active line at a given position. */
    fun activeIndex(lyrics: Lyrics, positionMs: Long): Int {
        if (lyrics.lines.isEmpty()) return -1
        var low = 0
        var high = lyrics.lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lyrics.lines[mid].timestampMs <= positionMs) {
                result = mid; low = mid + 1
            } else high = mid - 1
        }
        return result
    }
}
