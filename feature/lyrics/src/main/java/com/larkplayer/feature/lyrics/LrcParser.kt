package com.larkplayer.feature.lyrics

import com.larkplayer.core.common.LyricsTextUtils
import com.larkplayer.core.model.LyricLine
import com.larkplayer.core.model.Lyrics

/** Parser for LRC (`[mm:ss.xx]text`) and plain lyric text. */
object LrcParser {
    /** Accepts `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]` and `mm:ss:xx`. */
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val METADATA_TAG = Regex("""^\[[a-zA-Z]+:""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)

    fun parse(songId: Long, raw: String): Lyrics {
        val cleaned = LyricsTextUtils.clean(raw)
        if (cleaned.isBlank()) return Lyrics(songId)

        // LRC files often carry [offset:±xxx]; ignoring it made every line
        // consistently early or late. Apply it to all timestamps once here.
        val offsetMs = OFFSET_TAG.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = mutableListOf<LyricLine>()
        val seen = HashSet<String>()
        cleaned.lineSequence().forEach { source ->
            val line = source.trim()
            if (line.isEmpty() || METADATA_TAG.containsMatchIn(line)) return@forEach

            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = TIME_TAG.replace(line, "").let { LyricsTextUtils.clean(it) }.trim()
            if (text.isEmpty()) return@forEach

            tags.forEach { tag ->
                val timestamp = timestampOf(tag)
                val key = "$timestamp\u0000$text"
                if (seen.add(key)) timed += LyricLine(timestamp + offsetMs, text)
            }
        }
        if (timed.isNotEmpty()) {
            return Lyrics(songId, timed.sortedBy { it.timestampMs }, cleaned, offsetMs)
        }

        // No timestamps at all: keep the text so the UI can show scrollable,
        // unhighlighted lyrics instead of the "no lyrics" empty state.
        val plain = cleaned.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .map { LyricLine(-1L, it) }.toList()
        return Lyrics(songId, plain, cleaned, offsetMs)
    }

    /**
     * Plain lyrics have no timestamps. Distributing them evenly makes short
     * verses drag and long verses rush, so weight each line by its length to
     * follow the singing far more closely.
     */
    fun activeIndexForPlain(lyrics: Lyrics, positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0 || lyrics.lines.isEmpty()) return -1
        val weights = lyrics.lines.map { 6f + it.text.length * 0.7f }
        val total = weights.sum()
        val target = positionMs.coerceIn(0L, durationMs).toFloat() / durationMs * total
        var acc = 0f
        weights.forEachIndexed { index, weight ->
            acc += weight
            if (target <= acc) return index
        }
        return lyrics.lines.lastIndex
    }

    private fun timestampOf(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3]
        val millis = when {
            fraction.isBlank() -> 0L
            fraction.length == 1 -> fraction.toLong() * 100L
            fraction.length == 2 -> fraction.toLong() * 10L
            else -> fraction.take(3).toLong()
        }
        return maxOf(0L, minutes * 60_000L + seconds * 1_000L + millis)
    }

    /** Index of the active line at a given position, or -1 for unsynced lyrics. */
    fun activeIndex(lyrics: Lyrics, positionMs: Long): Int {
        if (!lyrics.isSynced) return -1
        var low = 0
        var high = lyrics.lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lyrics.lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
