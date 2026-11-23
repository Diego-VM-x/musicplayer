package com.larkplayer.feature.lyrics

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.larkplayer.core.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Estimates a start time for every line of a plain (untimed) lyric file by
 * looking at the song's own audio energy.
 *
 * The original Lark Player does NOT do this — it only uses the timestamps that
 * come in the .lrc/metadata. This component exists for the very common case of
 * a plain .txt/USLT lyric, where distributing lines evenly drifts with the
 * tempo. It runs once in the background, in return for no user interaction.
 */
object LyricsAutoSync {
    private const val TARGET_RATE = 16_000
    private const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz
    private const val MIN_GAP_MS = 220L
    private const val MAX_SECONDS = 300
    private const val SNAP_WINDOW_MS = 2_500L

    suspend fun durationMs(path: String): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun align(
        path: String,
        lines: List<LyricLine>,
        durationMs: Long,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (path.isBlank() || lines.size < 2) return@withContext null
        val samples = decodeMono(path) ?: return@withContext null
        if (samples.size < TARGET_RATE) return@withContext null

        val totalMs = if (durationMs > 0) durationMs else samples.size * 1000L / TARGET_RATE
        if (totalMs < 5_000L) return@withContext null

        val energies = frameEnergies(samples)
        val onsets = detectOnsets(energies)
        if (onsets.size < 2) return@withContext null

        // Longer lines (and more vowel groups) need more time than short ones.
        val weights = lines.map { lineWeight(it.text) }
        val totalWeight = weights.sum()
        var elapsed = 0f
        var previous = 0L
        val result = mutableListOf<LyricLine>()
        lines.forEachIndexed { index, line ->
            elapsed += weights[index]
            val target = elapsed / totalWeight * totalMs
            val snapped = onsets
                .filter { it > previous + MIN_GAP_MS / 2 && abs(it - target) <= SNAP_WINDOW_MS }
                .minByOrNull { abs(it - target) }
            val stamp = when {
                index == 0 -> (snapped ?: target).toLong().coerceAtLeast(0L)
                else -> (snapped ?: target).toLong().coerceAtLeast(previous + MIN_GAP_MS)
            }
            previous = stamp
            result += LyricLine(stamp, line.text)
        }
        result.takeIf { it.size == lines.size && it.last().timestampMs < totalMs + 5_000L }
    }

    /** Decodes the audio track to mono PCM at [TARGET_RATE] Hz. */
    private fun decodeMono(path: String): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val chunks = ArrayList<FloatArray>()
        var total = 0
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)

            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            @Suppress("DEPRECATION")
            val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            @Suppress("DEPRECATION")
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val step = max(1, sourceRate / TARGET_RATE)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val limit = TARGET_RATE * MAX_SECONDS

            while (!outputDone && total < limit) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val input = codec.getInputBuffer(inIndex) ?: break
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> {
                        if (outIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outIndex)
                            if (buffer != null && info.size >= 2) {
                                val wanted = minOf(total + info.size / 2 / channels, limit) - total
                                if (wanted > 0) {
                                    val mono = FloatArray(wanted)
                                    var written = 0
                                    var read = 0
                                    while (written < wanted && read + 1 < info.size) {
                                        // Keep only channel 0 (or the average of the pair).
                                        val sample = buffer.getShort(info.offset + read).toFloat() / 32768f
                                        read += 2
                                        if (channels >= 2 && read + 1 < info.size) {
                                            val right = buffer.getShort(info.offset + read).toFloat() / 32768f
                                            read += 2
                                            mono[written++] = (sample + right) * 0.5f
                                        } else {
                                            mono[written++] = sample
                                            read += 2 * (channels - 1)
                                        }
                                    }
                                    // Downsample to TARGET_RATE by keeping every `step`-th sample.
                                    val count = (written + step - 1) / step
                                    val reduced = FloatArray(count)
                                    var src = 0
                                    var dst = 0
                                    while (src < written && dst < count) {
                                        reduced[dst++] = mono[src]
                                        src += step
                                    }
                                    chunks += reduced
                                    total += count
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } catch (t: Throwable) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
        if (total == 0) return null
        val result = FloatArray(total)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        return result
    }

    private fun frameEnergies(samples: FloatArray): FloatArray {
        val count = samples.size / FRAME_SAMPLES
        val result = FloatArray(count)
        for (frame in 0 until count) {
            var sum = 0.0
            val base = frame * FRAME_SAMPLES
            for (i in 0 until FRAME_SAMPLES) {
                val v = samples[base + i]
                sum += v.toDouble() * v
            }
            result[frame] = kotlin.math.sqrt(sum / FRAME_SAMPLES).toFloat()
        }
        return result
    }

    /** Rising energy above a short moving average = likely start of a phrase. */
    private fun detectOnsets(energies: FloatArray): List<Long> {
        val onsets = ArrayList<Long>()
        onsets.add(0L)
        val window = 10 // 200 ms
        var last = -MIN_GAP_MS
        for (i in window until energies.size) {
            var sum = 0f
            for (j in i - window until i) sum += energies[j]
            val average = sum / window
            val time = i * FRAME_SAMPLES * 1000L / TARGET_RATE
            if (energies[i] > average * 1.5f && energies[i] > 0.012f && time - last >= MIN_GAP_MS) {
                onsets += time
                last = time
            }
        }
        return onsets
    }

    /** Rough duration weight: vowel groups and character count. */
    private fun lineWeight(text: String): Float {
        val clean = text.trim()
        if (clean.isEmpty()) return 2f
        val vowelGroups = Regex("[aeiouáéíóúüAEIOUÁÉÍÓÚÜ]+").findAll(clean).count()
        return max(2f, vowelGroups * 1.6f + clean.length / 12f)
    }

    fun toLrc(lines: List<LyricLine>): String = lines.joinToString("\n") {
        "[${lrcTime(it.timestampMs)}]${it.text}"
    }

    private fun lrcTime(ms: Long): String {
        val total = maxOf(0L, ms) / 1000
        return String.format(Locale.US, "%02d:%02d.%02d", total / 60, total % 60, (maxOf(0L, ms) % 1000) / 10)
    }
}
