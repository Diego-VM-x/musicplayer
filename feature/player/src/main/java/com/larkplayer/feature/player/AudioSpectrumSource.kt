package com.larkplayer.feature.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** 16 log-spaced bands, waveform snapshot and derived low/mid/high levels. */
data class AudioBands(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val spectrum: FloatArray = FloatArray(16),
    val peaks: FloatArray = FloatArray(16),
    val waveform: FloatArray = FloatArray(64),
    /** Slowly advancing phase for the wave styles, so they flow even on a
     *  steady note. Amplitude still comes exclusively from the real PCM. */
    val phase: Float = 0f,
    val fallback: Boolean = false,
)

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
    private val waveformSnapshot = FloatArray(64)

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
                val mono = sum / channels.coerceAtLeast(1)
                samples[writeIndex] = mono
                writeIndex = (writeIndex + 1) % WINDOW
                if (filled < WINDOW) filled++
                // Keep a compact waveform snapshot for organic modes (Flujo).
                val wIndex = (writeIndex % waveformSnapshot.size)
                waveformSnapshot[wIndex] = mono.coerceIn(-1f, 1f)
            }
        }
    }

    fun currentWaveform(): FloatArray = synchronized(lock) { waveformSnapshot.copyOf() }

    /** 16 log-spaced levels from 40 Hz to 16 kHz, normalized 0f..1f. */
    fun currentBands(): FloatArray {
        val result = FloatArray(BAND_COUNT)
        val (buffer, rate, available) = synchronized(lock) {
            val count = filled
            val ordered = FloatArray(count)
            for (i in 0 until count) {
                ordered[i] = samples[(writeIndex - count + i + WINDOW) % WINDOW]
            }
            Triple(ordered, sampleRate, count)
        }
        if (available < 128) return result

        val maxFreq = minOf(16_000f, rate / 2f)
        // Raw magnitude per band. The old `1 - exp(-raw*5)` + AGC pinned every
        // band to 0.98 with normal music, so all bars looked identical/frozen.
        val magnitudes = FloatArray(BAND_COUNT)
        var total = 0f
        for (band in 0 until BAND_COUNT) {
            val f0 = 40.0 * Math.pow((maxFreq / 40.0).toDouble(), band / BAND_COUNT.toDouble())
            val f1 = 40.0 * Math.pow((maxFreq / 40.0).toDouble(), (band + 1) / BAND_COUNT.toDouble())
            val center = sqrt(f0 * f1).toFloat()
            val magnitude = goertzel(buffer, center, rate)
            magnitudes[band] = magnitude
            total += magnitude
        }
        val mean = total / BAND_COUNT
        // Loudness gate: silence stays flat instead of being amplified into motion.
        val loudness = (rms() / 0.10f).coerceIn(0f, 1f)
        for (band in 0 until BAND_COUNT) {
            val relative = magnitudes[band] / (mean + 1e-6f)
            result[band] = (loudness * (0.10f + 0.90f * (relative / 2.2f).coerceIn(0f, 1f)))
                .coerceIn(0.02f, 1f)
        }
        return result
    }

    fun reset() {
        synchronized(lock) {
            writeIndex = 0
            filled = 0
        }
    }

    /** RMS level of the current window, used as a loudness gate for the bands. */
    fun rms(): Float {
        val (buffer, available) = synchronized(lock) {
            val ordered = FloatArray(filled)
            for (i in 0 until filled) {
                ordered[i] = samples[(writeIndex - filled + i + WINDOW) % WINDOW]
            }
            ordered to filled
        }
        if (available < 64) return 0f
        var sum = 0.0
        for (v in buffer) sum += v.toDouble() * v
        return sqrt(sum / buffer.size).toFloat()
    }

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
        // Plain magnitude; normalization against the other bands happens in
        // currentBands(), so no empirical multiplier is needed here.
        return sqrt(maxOf(0.0, power)).toFloat()
    }
}
