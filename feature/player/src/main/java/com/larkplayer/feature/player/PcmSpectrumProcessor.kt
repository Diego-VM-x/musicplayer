package com.larkplayer.feature.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pass-through PCM processor that taps the audio going to the speaker and
 * feeds [AudioSpectrumSource]. It does not change the audio at all.
 */
class PcmSpectrumProcessor : AudioProcessor {
    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var output: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var pendingEndOfStream = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        inputFormat = inputAudioFormat
        output = AudioProcessor.EMPTY_BUFFER
        AudioSpectrumSource.reset()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return
        val bytes = ByteArray(remaining)
        inputBuffer.get(bytes)

        if (output.capacity() < remaining) {
            output = ByteBuffer.allocateDirect(remaining * 2)
        }
        output.clear()

        when (inputFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> {
                val fb = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
                val frames = minOf(2048, fb.remaining())
                val pcm = FloatArray(frames)
                fb.get(pcm)
                AudioSpectrumSource.feed(pcm, frames, inputFormat.sampleRate)
            }
            C.ENCODING_PCM_16BIT -> {
                val sb = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer()
                val frames = minOf(2048, sb.remaining())
                val shorts = ShortArray(frames)
                sb.get(shorts)
                val floats = FloatArray(frames) { shorts[it] / 32768f }
                AudioSpectrumSource.feed(floats, frames, inputFormat.sampleRate)
            }
            else -> Unit
        }
        output.put(bytes)
        output.flip()
    }

    override fun queueEndOfStream() {
        pendingEndOfStream = true
    }

    override fun getOutput(): ByteBuffer = output

    override fun isEnded(): Boolean = pendingEndOfStream && !output.hasRemaining()

    override fun flush() {
        output = AudioProcessor.EMPTY_BUFFER
        pendingEndOfStream = false
        AudioSpectrumSource.reset()
    }

    override fun reset() {
        inputFormat = AudioFormat.NOT_SET
        flush()
    }
}
