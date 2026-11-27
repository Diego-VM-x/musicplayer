package com.larkplayer.feature.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Double-tap seek: left third goes back, right third goes forward, never past the end. */
class VideoGesturesTest {

    @Test
    fun `tap on left third seeks back ten seconds`() {
        assertEquals(-10_000L, VideoGestures.seekDelta(100f, 1_000, 60_000L, 180_000L))
    }

    @Test
    fun `tap on right third seeks forward ten seconds`() {
        assertEquals(10_000L, VideoGestures.seekDelta(900f, 1_000, 60_000L, 180_000L))
    }

    @Test
    fun `tap in the middle does not seek`() {
        assertEquals(0L, VideoGestures.seekDelta(500f, 1_000, 60_000L, 180_000L))
    }

    @Test
    fun `forward seek is clamped to the remaining duration`() {
        assertEquals(5_000L, VideoGestures.seekDelta(900f, 1_000, 175_000L, 180_000L))
    }

    @Test
    fun `backward seek is clamped to zero`() {
        assertEquals(-3_000L, VideoGestures.seekDelta(100f, 1_000, 3_000L, 180_000L))
    }
}
