package com.larkplayer.feature.lyrics.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import com.larkplayer.core.model.LyricLine
import com.larkplayer.core.model.Lyrics
import com.larkplayer.feature.lyrics.LrcParser
import com.larkplayer.feature.player.AudioSpectrumSource
import kotlin.math.abs

/**
 * Frame-bound lyric clock.
 *
 * `PlayerViewModel.uiState.position` is refreshed only every 250 ms. The ticker
 * is the ground truth (it is the one that sees seeks, pauses and track changes),
 * while `withFrameNanos` only interpolates *between* two ticks so the karaoke
 * fill moves smoothly instead of stepping four times a second.
 *
 * The three published states are read by [LyricsScreen] during composition —
 * notably [positionMs], which used to be consumed only inside a draw lambda.
 * Reading it in composition is what guarantees that every interpolated frame
 * actually invalidates the highlighted row and its clip rectangle.
 *
 * The audio pulse is cosmetic only: it never feeds back into position, active
 * line or word progress.
 */
internal class LyricsPlaybackClock {
    private val _positionMs = mutableLongStateOf(0L)
    val positionMs: State<Long> get() = _positionMs

    private val _activeLine = mutableIntStateOf(-1)
    val activeLine: State<Int> get() = _activeLine

    private val _pulse = mutableFloatStateOf(0f)
    val pulse: State<Float> get() = _pulse

    /** Latest manual sync correction; applied once to lines and words alike. */
    private var manualOffsetMs: Int = 0

    suspend fun run(
        tickerPositionMs: () -> Long,
        isPlaying: () -> Boolean,
        playbackSpeed: () -> Float,
        songKey: () -> String,
        lyrics: () -> Lyrics,
        offsetMs: () -> Int,
        pulseEnabled: () -> Boolean,
    ) {
        var lastTicker = Long.MIN_VALUE
        var anchorMs = 0L
        var anchorNanos = 0L
        var lastSongKey: String? = null
        var wasPlaying = false
        var smoothedPulse = 0f

        while (true) {
            withFrameNanos { now ->
                manualOffsetMs = offsetMs()

                val key = songKey()
                val tick = tickerPositionMs().coerceAtLeast(0L)
                val playing = isPlaying()
                val speed = playbackSpeed().coerceIn(0.1f, 4f)

                val trackChanged = lastSongKey != key
                val seeked = LyricsClock.isSeek(lastTicker, tick, speed)
                val pausedTick = !playing && tick != lastTicker
                val resumed = playing && !wasPlaying
                wasPlaying = playing

                // On a real seek / track change / pause / resume we drop the
                // extrapolation completely: the ticker value is exact at that
                // instant. In particular, resuming must not add the whole
                // paused interval to the interpolated position.
                if (trackChanged || seeked || pausedTick || resumed ||
                    lastTicker == Long.MIN_VALUE
                ) {
                    anchorMs = tick
                    anchorNanos = now
                    lastSongKey = key
                    if (trackChanged || seeked) {
                        _activeLine.value = -1
                        smoothedPulse = 0f
                    }
                } else if (tick != lastTicker) {
                    // Normal 250 ms tick. Re-anchor instead of converging at
                    // 35 % per tick: the old accumulator never caught up after a
                    // resume, so the highlight lagged (or froze) behind the song.
                    anchorMs = tick
                    anchorNanos = now
                }
                lastTicker = tick

                val position = if (playing) {
                    anchorMs + ((now - anchorNanos) / 1_000_000f * speed).toLong()
                } else {
                    anchorMs
                }.coerceAtLeast(0L)

                // One position, one offset, one active-line decision. Publishing
                // all three in the same frame prevents line highlight and fill
                // from disagreeing about where the song actually is.
                _positionMs.value = position
                val current = lyrics()
                val active = if (current.isSynced) {
                    LrcParser.activeIndex(current, position, manualOffsetMs)
                } else -1
                if (_activeLine.value != active) _activeLine.value = active

                // Optional and subtle: tied to the existing visualizer setting.
                // Guarded so a bad PCM reading can never kill the frame loop.
                val target = if (pulseEnabled() && playing && active >= 0) {
                    val level = runCatching { AudioSpectrumSource.level() }.getOrDefault(0f)
                    if (level.isFinite()) {
                        (level.coerceIn(0f, PULSE_REFERENCE) / PULSE_REFERENCE) * 0.55f
                    } else 0f
                } else 0f
                val factor = if (target > smoothedPulse) PULSE_ATTACK else PULSE_RELEASE
                smoothedPulse += (target - smoothedPulse) * factor
                if (abs(smoothedPulse - _pulse.value) > 0.002f) _pulse.value = smoothedPulse
            }
        }
    }

    /** Overall fill of a line, 0f..1f, for the supplied (smoothed) position. */
    fun lineProgress(line: LyricLine, positionMs: Long): Float {
        if (line.timestampMs < 0) return 0f
        val effective = positionMs + manualOffsetMs
        val start = line.timestampMs
        val end = lineEnd(line)
        if (effective <= start) return 0f
        if (effective >= end) return 1f
        return (effective - start).toFloat() / (end - start)
    }

    /** Fill of one word/syllable, 0f..1f, for the supplied (smoothed) position. */
    fun wordProgress(line: LyricLine, wordIndex: Int, positionMs: Long): Float {
        val word = line.words.getOrNull(wordIndex) ?: return lineProgress(line, positionMs)
        val effective = positionMs + manualOffsetMs
        val end = lineEnd(line)
        val start = if (word.timestampMs >= 0) word.timestampMs else line.timestampMs
        val nextStart = line.words.getOrNull(wordIndex + 1)?.timestampMs
            ?.takeIf { it > start && it <= end }
            ?: end
        if (effective <= start) return 0f
        if (effective >= nextStart) return 1f
        return (effective - start).toFloat() / (nextStart - start)
    }

    private fun lineEnd(line: LyricLine): Long {
        val end = line.endMs
        return if (end > line.timestampMs) end else line.timestampMs + DEFAULT_LINE_SPAN_MS
    }

    private companion object {
        const val DEFAULT_LINE_SPAN_MS = 2_000L
        const val PULSE_REFERENCE = 0.35f
        const val PULSE_ATTACK = 0.28f
        const val PULSE_RELEASE = 0.08f
    }
}
