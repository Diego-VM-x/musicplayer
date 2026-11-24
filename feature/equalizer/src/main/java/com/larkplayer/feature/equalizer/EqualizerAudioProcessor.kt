package com.larkplayer.feature.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real Media3 audio processor: a cascade of peaking biquad filters, one per
 * original Lark band. It can be installed in any DefaultAudioSink through
 * `setAudioProcessors(arrayOf(...))`.
 *
 * The live audio path in this build is the platform `audiofx.Equalizer`
 * attached to the ExoPlayer audio session (exactly what Lark 2027.1.8 does —
 * see MotionEqFragment.java:8,566). The sink itself is created in
 * feature/player/PlayerModule.kt, which is out of scope for this task, so this
 * processor mirrors the very same gains and is ready for sink integration.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    @Volatile private var enabled = false
    @Volatile private var levelsDb = IntArray(0)
    @Volatile private var cutoffsHz = EqPresets.CUTOFFS_5
    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_PCM_16BIT
    private var banks = emptyArray<Array<Biquad>>()

    /** [-15..15] from the original preset tables; one unit is one dB (100 millibel). */
    fun configureBands(enabled: Boolean, cutoffsHz: IntArray, levelsDb: IntArray) {
        this.enabled = enabled
        this.cutoffsHz = cutoffsHz.copyOf()
        this.levelsDb = levelsDb.copyOf()
        rebuild()
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        rebuild()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = enabled && banks.isNotEmpty()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return
        if (!isActive) {
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * channels
        val frames = remaining / frameBytes
        val output = replaceOutputBuffer(remaining)
        output.order(ByteOrder.nativeOrder())

        repeat(frames) {
            for (ch in 0 until channels) {
                val input = if (encoding == C.ENCODING_PCM_FLOAT) {
                    inputBuffer.getFloat()
                } else {
                    inputBuffer.getShort() / 32768f
                }
                var value = input
                val chain = banks[ch]
                var i = 0
                while (i < chain.size) {
                    value = chain[i].process(value)
                    i++
                }
                if (encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(value.coerceIn(-1f, 1f))
                } else {
                    val pcm = (value * 32767f).toInt().coerceIn(-32768, 32767)
                    output.putShort(pcm.toShort())
                }
            }
        }
        // Pass through any trailing bytes that do not form a whole frame.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    override fun onFlush() {
        banks.forEach { chain -> chain.forEach { it.reset() } }
    }

    override fun onReset() {
        banks = emptyArray()
        sampleRate = 0
        channels = 0
    }

    private fun rebuild() {
        if (sampleRate <= 0 || channels <= 0 || levelsDb.isEmpty()) {
            banks = emptyArray()
            return
        }
        val count = minOf(levelsDb.size, cutoffsHz.size)
        if (count == 0) {
            banks = emptyArray()
            return
        }
        banks = Array(channels) { ch ->
            IntRange(0, count - 1).map { i ->
                Biquad().apply {
                    configure(
                        sampleRate = sampleRate.toFloat(),
                        frequencyHz = cutoffsHz[i].toFloat(),
                        gainDb = levelsDb[i].toFloat(),
                    )
                }
            }.toTypedArray()
        }
    }

    /** RBJ peaking EQ biquad. Stable for the original +/-15 dB range. */
    private class Biquad {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun configure(sampleRate: Float, frequencyHz: Float, gainDb: Float) {
            val freq = frequencyHz.coerceIn(20f, (sampleRate * 0.45f))
            val w0 = 2f * PI.toFloat() * freq / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / 2f // Q = 1 / sqrt(2) would be 0.707; 1.0 matches Lark's narrow bands
            val a = 10f.pow(gainDb / 40f)
            val b0r = 1f + alpha * a
            val b1r = -2f * cosW
            val b2r = 1f - alpha * a
            val a0r = 1f + alpha / a
            val a1r = -2f * cosW
            val a2r = 1f - alpha / a
            b0 = b0r / a0r
            b1 = b1r / a0r
            b2 = b2r / a0r
            a1 = a1r / a0r
            a2 = a2r / a0r
            reset()
        }

        fun process(input: Float): Float {
            val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = out
            return if (out.isFinite()) out.coerceIn(-2f, 2f) else 0f
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }
}
