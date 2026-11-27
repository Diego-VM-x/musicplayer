package com.larkplayer.feature.lyrics.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** At 2x a normal 250 ms tick advances 500 ms and must not look like a seek. */
class LyricsClockTest {

    @Test
    fun `regular tick at double speed is not a seek`() {
        assertFalse(LyricsClock.isSeek(1_000L, 1_500L, 2f))
    }

    @Test
    fun `regular tick at normal speed is not a seek`() {
        assertFalse(LyricsClock.isSeek(1_000L, 1_250L, 1f))
    }

    @Test
    fun `forward seek is detected`() {
        assertTrue(LyricsClock.isSeek(1_000L, 5_000L, 1f))
    }

    @Test
    fun `backward seek is detected`() {
        assertTrue(LyricsClock.isSeek(5_000L, 100L, 2f))
    }

    @Test
    fun `first frame is not a seek`() {
        assertFalse(LyricsClock.isSeek(Long.MIN_VALUE, 0L, 1f))
    }
}
