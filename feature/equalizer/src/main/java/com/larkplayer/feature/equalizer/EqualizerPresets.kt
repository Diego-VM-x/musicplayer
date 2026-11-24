package com.larkplayer.feature.equalizer

import com.larkplayer.feature.equalizer.R

/**
 * Presets copied verbatim from the original Lark Player 2027.1.8.
 *
 * Names / order: `o/k20.java:42` (`f19511o`, the "no translate" preset names).
 * Localised name ids: `com/dywx/v4/gui/fragment/MotionEqFragment.java:87` (`C`).
 * Icon ids: `MotionEqFragment.java:88` (`D`).
 * 5-band levels: `o/k20.java:43` (`p`).
 * 10-band levels: `o/k20.java:46` (`f19512q`).
 * Band cutoffs: `o/k20.java:26` (5 bands), `o/k20.java:27` (10 bands).
 * Level range: `o/k20.java:38-39` (-15..15, one unit = 100 millibel).
 */
object EqPresets {
    const val CUSTOM = 0
    const val MIN_LEVEL = -15
    const val MAX_LEVEL = 15

    val CUTOFFS_5 = intArrayOf(60, 230, 910, 4_000, 14_000)
    val CUTOFFS_10 = intArrayOf(31, 63, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

    data class Preset(
        val id: Int,
        val name: String,
        val iconRes: Int,
        val levels5: IntArray,
        val levels10: IntArray,
    ) {
        fun levelsFor(bandCount: Int): IntArray = when {
            bandCount == levels5.size -> levels5.copyOf()
            bandCount == levels10.size -> levels10.copyOf()
            bandCount <= 0 -> IntArray(0)
            else -> resample(levels5, bandCount)
        }
    }

    val ALL: List<Preset> = listOf(
        Preset(0, "Custom", R.drawable.ic_eq_custom,
            intArrayOf(0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        Preset(1, "Bass booster", R.drawable.ic_eq_bass,
            intArrayOf(6, 4, 1, 0, 0),
            intArrayOf(5, 5, 3, 1, 0, 0, 0, 0, 0, 0)),
        Preset(2, "Vocal booster", R.drawable.ic_eq_vocal,
            intArrayOf(-2, -2, 4, 5, -1),
            intArrayOf(0, 0, 0, 0, 1, 4, 3, 4, 0, 0)),
        Preset(3, "Treble booster", R.drawable.ic_eq_treble,
            intArrayOf(0, 0, 1, 3, 5),
            intArrayOf(0, 0, 0, 0, 0, 1, 2, 2, 3, 5)),
        Preset(4, "Rock", R.drawable.ic_eq_rock,
            intArrayOf(5, 3, -1, 3, 5),
            intArrayOf(5, 3, 2, 0, 0, 1, 0, 2, 2, 4)),
        Preset(5, "Classical", R.drawable.ic_eq_classic,
            intArrayOf(5, 3, -2, 4, 4),
            intArrayOf(0, 1, -2, 0, -1, 0, 2, -2, -2, 3)),
        Preset(6, "Electronic", R.drawable.ic_eq_edm,
            intArrayOf(3, -1, 2, 1, 4),
            intArrayOf(4, 7, 6, -1, -2, 0, 5, 8, 3, 5)),
        Preset(7, "HipHop", R.drawable.ic_eq_hiphop,
            intArrayOf(5, 3, 0, 1, 3),
            intArrayOf(5, 5, 3, 0, -2, -2, 2, 3, 4, 4)),
        Preset(8, "Dance", R.drawable.ic_eq_dance,
            intArrayOf(6, 0, 2, 4, 1),
            intArrayOf(5, 5, 4, 2, 1, 3, 4, 3, 2, 0)),
        Preset(9, "Jazz", R.drawable.ic_eq_jazz,
            intArrayOf(4, 2, -2, 2, 5),
            intArrayOf(3, 3, 2, 0, -1, -1, 0, 2, 3, 4)),
        Preset(10, "Pop", R.drawable.ic_eq_pop,
            intArrayOf(-1, 2, 5, 1, -2),
            intArrayOf(-1, -1, 0, 1, 1, 2, 2, 1, 0, -1)),
        Preset(11, "R&B", R.drawable.ic_eq_rnb,
            intArrayOf(5, 4, -2, 2, 3),
            intArrayOf(5, 5, 4, 0, -2, -1, 2, 2, 2, 3)),
        Preset(12, "Heavy metal", R.drawable.ic_eq_heavymetal,
            intArrayOf(4, 1, 9, 3, 0),
            intArrayOf(2, 4, 3, 1, 2, 3, 3, 2, 2, 0)),
        Preset(13, "Folk", R.drawable.ic_eq_folk,
            intArrayOf(3, 0, 0, 2, -1),
            intArrayOf(0, 2, 3, 0, 0, 0, 6, 3, 2, -1)),
        Preset(14, "Latin", R.drawable.ic_eq_latin,
            intArrayOf(5, 0, -2, -2, 6),
            intArrayOf(1, 3, 2, 0, 0, -1, 0, -1, 0, 3)),
        Preset(15, "Normal", R.drawable.ic_eq_normal,
            intArrayOf(3, 0, 0, 0, 3),
            intArrayOf(3, 1, 1, 0, -1, 0, 0, 0, 1, 1)),
    )

    fun preset(id: Int): Preset = ALL.getOrNull(id) ?: ALL.first()

    /** Original cutoffs when known; otherwise log-spaced across the audible range. */
    fun cutoffsFor(bandCount: Int, sampleRate: Int = 48_000): IntArray = when (bandCount) {
        CUTOFFS_5.size -> CUTOFFS_5.copyOf()
        CUTOFFS_10.size -> CUTOFFS_10.copyOf()
        0 -> IntArray(0)
        else -> {
            val top = (sampleRate / 2f).coerceAtMost(20_000f)
            IntArray(bandCount) { i ->
                val t = if (bandCount == 1) 0.5f else i.toFloat() / (bandCount - 1)
                (20f * Math.pow((top / 20f).toDouble(), t.toDouble())).toInt()
            }
        }
    }

    private fun resample(source: IntArray, count: Int): IntArray {
        if (count == source.size) return source.copyOf()
        return IntArray(count) { i ->
            val pos = i.toFloat() * (source.size - 1) / maxOf(1, count - 1)
            val lo = pos.toInt()
            val hi = minOf(lo + 1, source.size - 1)
            val f = pos - lo
            kotlin.math.round(source[lo] * (1f - f) + source[hi] * f).toInt()
        }
    }
}
