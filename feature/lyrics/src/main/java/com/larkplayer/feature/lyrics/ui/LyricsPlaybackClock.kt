package com.larkplayer.feature.lyrics.ui

import androidx.compose.runtime.MutableState
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
 * `PlayerViewModel.uiState.position` is refreshed only every 250 ms, which is
 * exactly why the highlight used to jump behind (or ahead of) the voice. The
 * ticker remains the ground truth — seeks, pauses and track changes are read
 * from it — while `withFrameNanos` extrapolates between ticks and publishes:
 *  - a smooth position for the karaoke fill,
 *  - the active line index (changes rarely → cheap recomposition),
 *  - an optional, heavily smoothed audio-energy pulse.
 *
 * The pulse never feeds back into position or into the active-line calculation.
 */
internal class LyricsPlaybackClock {
    private val _positionMs = mutableLongStateOf(0L)
    val positionMs: MutableState<Long> get() = _positionMs

    private val _activeLine = mutableIntStateOf(-1)
    val activeLine: MutableState<Int> get() = _activeLine

    private val _pulse = mutableFloatStateOf(0f)
    val pulse: MutableState<Float> get() = _pulse

    suspend fun run(
        tickerPositionMs: () -> Long,
        isPlaying: () -> Boolean,
        playbackSpeed: () -> Float,
        songKey: () -> String,
        lyrics: () -> Lyrics,
        offsetMs: () -> Int,
        pulseEnabled: () -> Boolean,
    ) {
        var lastFrameNanos = 0L
        var lastTicker = Long.MIN_VALUE
        var estimated = 0L
        var lastSongKey = ""
        var smoothedPulse = 0f

        while (true) {
            withFrameNanos { now ->
                val deltaMs = if (lastFrameNanos == 0L) 0f
                else (now - lastFrameNanos) / 1_000_000f
                lastFrameNanos = now

                val key = songKey()
                if (key != lastSongKey) {
                    lastSongKey = key
                    lastTicker = Long.MIN_VALUE
                    estimated = tickerPositionMs().coerceAtLeast(0L)
                    smoothedPulse = 0f
                    _activeLine.value = -1
                }

                val tick = tickerPositionMs().coerceAtLeast(0L)
                if (tick != lastTicker) {
                    lastTicker = tick
                    val difference = tick - estimated
                    when {
                        // A real seek, a loop restart or a stale anchor: snap.
                        abs(difference) > SEEK_THRESHOLD_MS -> estimated = tick
                        // Paused: the ticker is exact, no extrapolation needed.
                        !isPlaying() -> estimated = tick
                        // Normal 250 ms tick: converge gently instead of jumping.
                        else -> estimated += (difference * TICK_CORRECTION).toLong()
                    }
                }
                if (isPlaying()) {
                    estimated += (deltaMs * playbackSpeed().coerceIn(0.1f, 4f)).toLong()
                }
                if (estimated < 0L) estimated = 0L
                _positionMs.value = estimated

                val current = lyrics()
                val effective = (estimated + offsetMs()).coerceAtLeast(0L)
                val active = if (current.isSynced) LrcParser.activeIndex(current, effective) else -1
                if (_activeLine.value != active) _activeLine.value = active

                // Optional and subtle: only while the existing visualizer
                // setting is on. Purely cosmetic; timing is already committed.
                val target = if (pulseEnabled() && isPlaying() && active >= 0) {
                    (AudioSpectrumSource.level().coerceIn(0f, PULSE_REFERENCE) / PULSE_REFERENCE) * 0.55f
                } else 0f
                val factor = if (target > smoothedPulse) PULSE_ATTACK else PULSE_RELEASE
                smoothedPulse += (target - smoothedPulse) * factor
                if (abs(smoothedPulse - _pulse.value) > 0.002f) _pulse.value = smoothedPulse
            }
        }
    }

    /** Overall fill of a line, 0f..1f. Safe to call from a draw phase. */
    fun lineProgress(line: LyricLine): Float {
        if (line.timestampMs < 0) return 0f
        val end = lineEnd(line)
        return ((_positionMs.value - line.timestampMs).toFloat() / (end - line.timestampMs))
            .coerceIn(0f, 1f)
    }

    /** Fill of one word/syllable, 0f..1f. Safe to call from a draw phase. */
    fun wordProgress(line: LyricLine, wordIndex: Int): Float {
        val word = line.words.getOrNull(wordIndex) ?: return lineProgress(line)
        val end = lineEnd(line)
        val start = if (word.timestampMs >= 0) word.timestampMs else line.timestampMs
        val nextStart = line.words.getOrNull(wordIndex + 1)?.timestampMs
            ?.takeIf { it > start && it <= end }
            ?: end
        return ((_positionMs.value - start).toFloat() / (nextStart - start)).coerceIn(0f, 1f)
    }

    private fun lineEnd(line: LyricLine): Long {
        val end = line.endMs
        return if (end > line.timestampMs) end else line.timestampMs + DEFAULT_LINE_SPAN_MS
    }

    private companion object {
        const val SEEK_THRESHOLD_MS = 500L
        const val TICK_CORRECTION = 0.35f
        const val DEFAULT_LINE_SPAN_MS = 2_000L
        const val PULSE_REFERENCE = 0.35f
        const val PULSE_ATTACK = 0.28f
        const val PULSE_RELEASE = 0.08f
    }
}
