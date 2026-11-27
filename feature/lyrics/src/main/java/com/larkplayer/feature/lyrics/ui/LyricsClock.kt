package com.larkplayer.feature.lyrics.ui

import kotlin.math.abs

/** Pure seek detection shared by the frame clock and its tests. */
internal object LyricsClock {
    private const val BASE_THRESHOLD_MS = 500L

    /**
     * The player publishes a position every 250 ms. At 2x that tick advances
     * 500 ms, which used to be classified as a seek and cleared the highlight
     * on every tick. Scale the threshold with playback speed instead.
     */
    fun isSeek(previousMs: Long, currentMs: Long, speed: Float): Boolean {
        if (previousMs == Long.MIN_VALUE) return false
        val threshold = maxOf(BASE_THRESHOLD_MS, (400f * speed.coerceIn(0.1f, 4f)).toLong())
        return abs(currentMs - previousMs) >= threshold
    }
}
