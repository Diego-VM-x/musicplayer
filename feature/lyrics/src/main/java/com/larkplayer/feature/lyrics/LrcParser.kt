package com.larkplayer.feature.lyrics

import com.larkplayer.core.common.LyricsTextUtils
import com.larkplayer.core.model.LyricLine
import com.larkplayer.core.model.LyricWord
import com.larkplayer.core.model.Lyrics

/**
 * Parser for LRC (`[mm:ss.xx]text`), enhanced LRC (`<mm:ss.xx>` word marks),
 * SYLT converted by [EmbeddedLyrics][com.larkplayer.core.common.EmbeddedLyrics]
 * and plain lyric text.
 *
 * The public shape of [Lyrics]/[LyricLine] is unchanged for callers that only
 * use `timestampMs`/`text`; word data is additive so Room (which stores the raw
 * text only) needs no migration.
 */
object LrcParser {
    /** Accepts `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]` and `mm:ss:xx`. */
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val METADATA_TAG = Regex("""^\[[a-zA-Z]+:""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)
    /** Keeps the trailing whitespace of a token so a Row of words reads naturally. */
    private val WORD_CHUNK = Regex("""\S+\s*""")
    private const val MIN_WORD_SPAN_MS = 90L
    private const val DEFAULT_LINE_SPAN_MS = 2_000L

    fun parse(songId: Long, raw: String): Lyrics {
        val cleaned = LyricsTextUtils.clean(raw)
        if (cleaned.isBlank()) return Lyrics(songId)

        // LRC files often carry [offset:±xxx]; ignoring it made every line
        // consistently early or late. Apply it to all timestamps once here.
        val offsetMs = OFFSET_TAG.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = mutableListOf<LyricLine>()
        val seen = HashSet<String>()
        var explicitWordMarks = false

        cleaned.lineSequence().forEach { source ->
            val line = source.trim()
            if (line.isEmpty() || METADATA_TAG.containsMatchIn(line)) return@forEach

            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = TIME_TAG.replace(line, "").let { LyricsTextUtils.clean(it) }.trim()
            if (text.isEmpty()) return@forEach

            val (tokens, explicit) = tokenize(text, offsetMs)
            if (explicit) explicitWordMarks = true

            tags.forEach { tag ->
                val timestamp = timestampOf(tag) + offsetMs
                val key = "$timestamp\u0000$text"
                if (seen.add(key)) {
                    timed += LyricLine(
                        timestampMs = timestamp,
                        text = text,
                        words = tokens,
                    )
                }
            }
        }

        if (timed.isNotEmpty()) {
            val sorted = timed.sortedBy { it.timestampMs }
            val finalized = sorted.mapIndexed { index, line ->
                val end = if (index < sorted.lastIndex) {
                    val next = sorted[index + 1].timestampMs
                    if (next > line.timestampMs) next
                    else line.timestampMs + DEFAULT_LINE_SPAN_MS
                } else {
                    val lastWordEnd = (line.words.lastOrNull()?.timestampMs ?: line.timestampMs)
                        + DEFAULT_LINE_SPAN_MS
                    maxOf(lastWordEnd, line.timestampMs + DEFAULT_LINE_SPAN_MS)
                }
                line.copy(
                    endMs = end,
                    words = finalizeWords(line.words, line.timestampMs, end, explicitWordMarks),
                )
            }
            return Lyrics(songId, finalized, cleaned, offsetMs, explicitWordMarks)
        }

        // No timestamps at all: keep the text so the UI can show clean,
        // scrollable, unhighlighted lyrics plus the "no timing" indicator.
        val plain = cleaned.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .map { LyricLine(-1L, it) }.toList()
        return Lyrics(songId, plain, cleaned, offsetMs, false)
    }

    /**
     * Splits a line into word/syllable tokens. With `<mm:ss.xx>` marks the
     * timestamp belongs to the text that FOLLOWS the mark; text before the
     * first mark (rare) inherits the line start. Without marks every token
     * gets -1 and is distributed in [finalizeWords].
     */
    private fun tokenize(text: String, offsetMs: Long): Pair<List<LyricWord>, Boolean> {
        val marks = WORD_TAG.findAll(text).toList()
        if (marks.isEmpty()) {
            return WORD_CHUNK.findAll(text).map { LyricWord(it.value) }.toList() to false
        }
        val tokens = mutableListOf<LyricWord>()
        var cursor = 0
        marks.forEachIndexed { index, mark ->
            val chunk = text.substring(cursor, mark.range.first)
            if (chunk.isNotBlank()) {
                val stamp = if (index == 0) -1L else timestampOf(marks[index - 1]) + offsetMs
                appendTokens(tokens, chunk, stamp)
            }
            cursor = mark.range.last + 1
        }
        val tail = text.substring(cursor)
        if (tail.isNotBlank()) {
            appendTokens(tokens, tail, timestampOf(marks.last()) + offsetMs)
        }
        return tokens to true
    }

    private fun appendTokens(target: MutableList<LyricWord>, chunk: String, stamp: Long) {
        WORD_CHUNK.findAll(chunk).forEach { target += LyricWord(it.value, stamp) }
    }

    /**
     * Resolves inherited word times. Explicit marks are interpolated only for
     * unmarked leading tokens; untimed lines get a length-weighted distribution
     * across the line span, which stays inside the real line boundaries.
     */
    private fun finalizeWords(
        words: List<LyricWord>,
        lineStart: Long,
        lineEnd: Long,
        explicit: Boolean,
    ): List<LyricWord> {
        if (words.isEmpty()) return words
        val end = maxOf(lineEnd, lineStart + MIN_WORD_SPAN_MS * words.size)
        if (!explicit) return distribute(words, lineStart, end)

        val known = words.mapIndexedNotNull { index, word ->
            if (word.timestampMs >= 0) index to word.timestampMs else null
        }
        if (known.isEmpty()) return distribute(words, lineStart, end)

        val anchors = mutableListOf<Pair<Int, Long>>()
        if (known.first().first > 0) anchors += 0 to lineStart
        anchors += known
        if (known.last().first < words.lastIndex) anchors += words.size to end

        return words.mapIndexed { index, word ->
            if (word.timestampMs >= 0) return@mapIndexed word
            val before = anchors.last { it.first <= index }
            val after = anchors.firstOrNull { it.first > index }
            val nextMs = after?.second?.takeIf { it > before.second } ?: before.second
            word.copy(
                timestampMs = before.second +
                    ((nextMs - before.second) * (index - before.first) /
                        (after?.first?.minus(before.first) ?: 1)).toLong(),
            )
        }
    }

    private fun distribute(words: List<LyricWord>, start: Long, end: Long): List<LyricWord> {
        val span = (end - start).coerceAtLeast(MIN_WORD_SPAN_MS * words.size)
        val weights = words.map { (it.text.trim().length + 1).toFloat() }
        val total = weights.sum()
        var accumulated = 0f
        return words.mapIndexed { index, word ->
            val stamp = start + (accumulated / total * span).toLong()
            accumulated += weights[index]
            word.copy(timestampMs = stamp)
        }
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
