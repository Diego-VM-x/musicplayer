package com.larkplayer.feature.player.ui

/** Pure seek math for the video player's double-tap gesture. */
internal object VideoGestures {
    private const val STEP_MS = 10_000L

    fun seekDelta(tapX: Float, viewWidth: Int, positionMs: Long, durationMs: Long): Long {
        if (viewWidth <= 0) return 0L
        val fraction = tapX / viewWidth
        val raw = when {
            fraction < 1f / 3f -> -STEP_MS
            fraction > 2f / 3f -> STEP_MS
            else -> return 0L
        }
        val target = (positionMs + raw).coerceAtLeast(0L)
        val clamped = if (durationMs > 0) target.coerceAtMost(durationMs) else target
        return clamped - positionMs
    }
}
