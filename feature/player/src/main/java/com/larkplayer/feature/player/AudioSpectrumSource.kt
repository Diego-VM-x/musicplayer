package com.larkplayer.feature.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Mono PCM ring buffer tapped from ExoPlayer's audio sink.
 *
 * This needs no microphone permission: it reads the same PCM that is being
 * sent to the speaker. Band levels are computed with the Goertzel algorithm,
 * which is cheaper and more stable than a full FFT for 16 fixed frequencies.
 */
object AudioSpectrumSource {
    const val BAND_COUNT = 16

    private const val WINDOW = 1024
    private val lock = Any()
    private val samples = FloatArray(WINDOW)
    private var writeIndex = 0
    private var filled = 0
    @Volatile private var sampleRate = 48_000

    fun feed(pcm: FloatArray, count: Int, rate: Int) {
        if (count <= 0) return
        synchronized(lock) {
            sampleRate = rate
            var i = 0
            while (i < count) {
                // Mono downmix of interleaved channels.
                var sum = 0f
                var channels = 0
                while (i < count && channels < 2) {
                    sum += pcm[i]
                    i++
                    channels++
                }
                samples[writeIndex] = sum / channels.coerceAtLeast(1)
                writeIndex = (writeIndex + 1) % WINDOW
                if (filled < WINDOW) filled++
            }
        }
    }

    /** 16 log-spaced levels from 40 Hz to 16 kHz, normalized 0f..1f. */
    fun currentBands(): FloatArray {
        val result = FloatArray(BAND_COUNT)
        val (buffer, rate, available) = synchronized(lock) {
            val ordered = FloatArray(availableOrZero())
            for (i in 0 until availableOrZero()) {
                ordered[i] = samples[(writeIndex - availableOrZero() + i + WINDOW) % WINDOW]
            }
            Triple(ordered, sampleRate, availableOrZero())
        }
        if (available < 64) return result

        val maxFreq = minOf(16_000f, rate / 2f)
        for (band in 0 until BAND_COUNT) {
            val f0 = 40.0 * Math.pow((maxFreq / 40.0).toDouble(), band / BAND_COUNT.toDouble())
            val f1 = 40.0 * Math.pow((maxFreq / 40.0).toDouble(), (band + 1) / BAND_COUNT.toDouble())
            val center = sqrt(f0 * f1).toFloat()
            result[band] = goertzel(buffer, center, rate).coerceIn(0f, 1f)
        }
        return result
    }

    fun reset() {
        synchronized(lock) {
            writeIndex = 0
            filled = 0
        }
    }

    private fun availableOrZero(): Int = synchronized(lock) { filled }

    private fun goertzel(input: FloatArray, freq: Float, rate: Int): Float {
        val n = input.size
        val k = (2f * PI * freq / rate).toDouble()
        val coeff = 2.0 * cos(k)
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until n) {
            s0 = input[i].toDouble() + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = (s1 * s1 + s2 * s2 - coeff * s1 * s2) / n
        // Empirical scale: typical music PCM peaks around 0.2-0.6 RMS.
        return (sqrt(maxOf(0.0, power)) * 6f).toFloat()
    }
}
