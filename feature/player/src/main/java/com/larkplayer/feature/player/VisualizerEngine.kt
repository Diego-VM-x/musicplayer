package com.larkplayer.feature.player

import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.Manifest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 16 log-spaced frequency bins plus wave data, all normalized 0f..1f. */
data class AudioBands(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val spectrum: FloatArray = FloatArray(16),
    val peaks: FloatArray = FloatArray(16),
    val waveform: FloatArray = FloatArray(64),
    /** True when a real Visualizer could not be attached. */
    val fallback: Boolean = false,
)

/**
 * Captures and normalizes audio for the visualizer.
 *
 * Primary source: [Visualizer] attached to the player's audio session.
 * Fallback: a soft simulated signal when RECORD_AUDIO is missing, the session
 * id is unavailable, or the effect cannot be created — the UI never crashes.
 *
 * Tuning: [SENSITIVITY], [SMOOTHING], [PEAK_DECAY], [BAND_COUNT].
 */
class VisualizerEngine(private val context: Context) {
    companion object {
        const val BAND_COUNT = 16
        /** Higher = more reactive. Keep 1..12. */
        private const val SENSITIVITY = 7f
        /** 0f..1f: how much of the previous frame is kept (damping). */
        private const val SMOOTHING = 0.72f
        /** Amount removed from each peak per frame. */
        private const val PEAK_DECAY = 0.018f
    }

    private val _bands = MutableStateFlow(AudioBands())
    val bands: StateFlow<AudioBands> = _bands

    private var scope: CoroutineScope? = null
    private var visualizer: Visualizer? = null
    private var isPlaying = false
    private var gain = 0f

    /** Attach to [audioSessionId]. Falls back automatically on any failure. */
    fun start(audioSessionId: Int, playing: Boolean) {
        release()
        isPlaying = playing
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { cs ->
            cs.launch { renderLoop() }
        }
        val allowed = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!allowed || audioSessionId <= 0) {
            markFallback(true)
            return
        }
        val result = runCatching {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?, waveform: ByteArray?, samplingRate: Int,
                        ) {
                            if (waveform == null) return
                            val out = FloatArray(min(64, waveform.size))
                            for (i in out.indices) out[i] = (waveform[i].toInt() and 0xFF) / 128f - 1f
                            update { it.copy(waveform = out) }
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?, fft: ByteArray?, samplingRate: Int,
                        ) {
                            if (fft == null) return
                            publish(analyzeFft(fft, samplingRate, FloatArray(BAND_COUNT)), _bands.value.waveform)
                        }
                    },
                    40, // ~25 fps capture; the canvas interpolates to 60 fps
                    true,
                    true,
                )
                enabled = true
            }
        }
        val vis = result.getOrNull()
        if (vis == null) {
            markFallback(true)
        } else {
            visualizer = vis
            markFallback(false)
        }
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
    }

    /** Releases the effect so the audio session is never left locked. */
    fun release() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        scope?.cancel()
        scope = null
        gain = 0f
    }

    private fun markFallback(active: Boolean) {
        update { it.copy(fallback = active) }
    }

    private fun update(reducer: (AudioBands) -> AudioBands) {
        _bands.value = reducer(_bands.value)
    }

    /** Android FFT layout: [0]=DC, [1]=Nyquist, then real/imag pairs. */
    private fun analyzeFft(fft: ByteArray, samplingRateHz: Int, previous: FloatArray): FloatArray {
        val bins = fft.size / 2
        if (bins < 2) return previous
        val binHz = (samplingRateHz / 1000f) / 2f / (bins - 1)
        val out = FloatArray(BAND_COUNT)
        val maxFreq = minOf(16_000f, (samplingRateHz / 1000f) / 2f)
        for (b in 0 until BAND_COUNT) {
            val f0 = 40.0 * Math.pow((maxFreq / 40.0).toDouble(), b / BAND_COUNT.toDouble())
            val f1 = 40.0 * Math.pow((maxFreq / 40.0).toDouble(), (b + 1) / BAND_COUNT.toDouble())
            var sum = 0f
            var count = 0
            for (i in 1 until bins) {
                val freq = binHz * i
                if (freq in f0..f1) {
                    val re = fft[2 * i].toInt() and 0xFF
                    val im = fft[2 * i + 1].toInt() and 0xFF
                    sum += kotlin.math.sqrt((re * re + im * im).toFloat()) / 128f
                    count++
                }
            }
            val raw = if (count > 0) sum / count else 0f
            out[b] = 1f - kotlin.math.exp(-raw * SENSITIVITY)
        }
        return out
    }

    /** Frame loop: fades to rest when paused, or generates a soft fallback. */
    private suspend fun renderLoop() {
        val random = Random(42)
        val phase = FloatArray(BAND_COUNT) { random.nextFloat() * 2f * PI.toFloat() }
        while (true) {
            val current = _bands.value
            val targetGain = if (isPlaying) 1f else 0f
            gain += (targetGain - gain) * if (targetGain > gain) 0.18f else 0.06f

            if (current.fallback) {
                val t = System.currentTimeMillis() / 1000f
                val synth = FloatArray(BAND_COUNT) { i ->
                    (0.32f + 0.22f * sin(t * (1.2f + i * 0.07f) + phase[i])) *
                        (0.6f + 0.4f * sin(t * 0.6f + i * 0.3f))
                }
                val wave = FloatArray(64) { i ->
                    sin(i * 0.22f + t * 3f) * 0.35f * gain
                }
                val spectrum = FloatArray(BAND_COUNT) { i ->
                    current.spectrum[i] * SMOOTHING + max(0f, synth[i]) * (1f - SMOOTHING) * gain
                }
                publish(spectrum, wave)
            } else if (gain <= 0.01f && current.spectrum.any { it > 0.01f }) {
                publish(FloatArray(BAND_COUNT), current.waveform)
            }
            delay(16L)
        }
    }

    private fun publish(spectrum: FloatArray, waveform: FloatArray) {
        update { current ->
            val smoothed = FloatArray(BAND_COUNT) { i ->
                val v = spectrum[i] * gain
                current.spectrum[i] * SMOOTHING + v * (1f - SMOOTHING)
            }
            val peaks = FloatArray(BAND_COUNT) { i ->
                max(smoothed[i], current.peaks[i] - PEAK_DECAY)
            }
            current.copy(
                spectrum = smoothed,
                peaks = peaks,
                waveform = waveform,
                bass = (smoothed.take(4).average().toFloat()).coerceIn(0f, 1f),
                mid = (smoothed.drop(4).take(8).average().toFloat()).coerceIn(0f, 1f),
                treble = (smoothed.drop(12).average().toFloat()).coerceIn(0f, 1f),
            )
        }
    }
}
