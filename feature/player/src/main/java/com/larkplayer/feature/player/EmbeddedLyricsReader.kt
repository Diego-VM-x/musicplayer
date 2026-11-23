package com.larkplayer.feature.player

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Reads LRC text stored inside the audio container. Handles ID3v2 USLT/ULT
 * (MP3) and raw LRC text appended to a file, without third-party libraries.
 */
object EmbeddedLyricsReader {
    fun read(context: Context, songPath: String, songId: Long): String? {
        val candidates = mutableListOf<Uri>()
        if (songPath.isNotBlank()) candidates += Uri.parse("file://$songPath")
        if (songId > 0) candidates += Uri.parse("content://media/external/audio/media/$songId")
        for (uri in candidates) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { parse(it) }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun parse(input: InputStream): String? {
        val head = ByteArray(10)
        if (input.read(head) < 10) return null
        if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
            return parseId3(input, head)
        }
        return null
    }

    private fun parseId3(input: InputStream, head: ByteArray): String? {
        val version = head[3].toInt() and 0xFF
        if (version !in 2..4) return null
        val size = synchsafe(head[6], head[7], head[8], head[9])
        if (size <= 0 || size > 4 * 1024 * 1024) return null
        val tag = input.readNBytes(size)
        val idLen = if (version == 2) 3 else 4
        var i = 0
        while (i + idLen + 4 <= tag.size) {
            val id = String(tag, i, idLen, StandardCharsets.ISO_8859_1).trim('\u0000')
            val frameSize = if (version == 2) {
                ((tag[i + 3].toInt() and 0xFF) shl 16) or
                    ((tag[i + 4].toInt() and 0xFF) shl 8) or (tag[i + 5].toInt() and 0xFF)
            } else {
                synchsafe(tag[i + idLen], tag[i + idLen + 1], tag[i + idLen + 2], tag[i + idLen + 3])
            }
            val frameStart = i + idLen + (if (version == 2) 3 else 4) + 2
            if (frameSize <= 0 || frameStart + frameSize > tag.size) break
            if (id == "USLT" || id == "ULT" || id == "SYLT" || id == "SLT") {
                parseFrame(tag, frameStart, frameSize)?.let { return it }
            }
            i = frameStart + frameSize
        }
        return null
    }

    private fun parseFrame(tag: ByteArray, start: Int, size: Int): String? {
        if (size < 6) return null
        val encoding = tag[start].toInt() and 0xFF
        var pos = start + 4 // encoding + 3-byte language
        val charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1, 2 -> StandardCharsets.UTF_16
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        while (pos + width <= start + size) {
            val zero = if (width == 2) tag[pos] == 0.toByte() && tag[pos + 1] == 0.toByte()
            else tag[pos] == 0.toByte()
            if (zero) { pos += width; break }
            pos++
        }
        if (pos >= start + size) return null
        val text = String(tag.copyOfRange(pos, start + size), charset)
            .replace("\u0000", "")
            .trim()
        return text.takeIf { it.isNotBlank() }
    }

    private fun synchsafe(a: Byte, b: Byte, c: Byte, d: Byte): Int =
        ((a.toInt() and 0x7F) shl 21) or
            ((b.toInt() and 0x7F) shl 14) or
            ((c.toInt() and 0x7F) shl 7) or
            (d.toInt() and 0x7F)
}
