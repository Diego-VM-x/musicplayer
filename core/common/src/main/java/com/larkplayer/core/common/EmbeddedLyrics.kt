package com.larkplayer.core.common

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Reads an embedded ID3v2 USLT/SYLT lyric frame from an audio file.
 * No third-party tag library is needed: the grammar is small and stable.
 */
object EmbeddedLyrics {
    private const val MAX_TAG_BYTES = 4 * 1024 * 1024

    /** Tries the file's MediaStore URI first, then the given path. */
    fun read(context: Context, song: com.larkplayer.core.model.Song): String? {
        val uris = listOf(
            song.contentUri,
            "content://media/external/audio/media/${song.id}",
        ).filter { it.isNotBlank() }
        for (uri in uris) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { readFrom(it) }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun readFrom(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.read(header) != header.size) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }
        val version = header[3].toInt() and 0xFF
        if (version !in 2..4) return null
        var offset = 10
        // Extended header (flag 0x40 in v2.3, 0x10 in v2.4): skip its declared size.
        if (header[5].toInt() and 0x40 != 0 || header[5].toInt() and 0x10 != 0) {
            val ext = ByteArray(4)
            if (input.read(ext) != ext.size) return null
            val extSize = sizeOf(ext, version, extended = true)
            skip(input, extSize)
            offset += 4 + extSize
        }
        val tagSize = sizeOf(header.copyOfRange(6, 10), version, extended = false)
        if (tagSize <= 0 || tagSize > MAX_TAG_BYTES) return null
        val tag = input.readNBytes(tagSize)
        if (tag.size < tagSize) return null

        var i = 0
        val idLen = if (version == 2) 3 else 4
        while (i + idLen + (if (version == 2) 3 else 4) + 2 <= tag.size) {
            val id = String(tag, i, idLen, StandardCharsets.ISO_8859_1).trim('\u0000')
            val size = if (version == 2) {
                ((tag[i + 3].toInt() and 0xFF) shl 16) or
                    ((tag[i + 4].toInt() and 0xFF) shl 8) or
                    (tag[i + 5].toInt() and 0xFF)
            } else {
                sizeOf(tag.copyOfRange(i + idLen, i + idLen + 4), version, extended = false)
            }
            val headerLen = idLen + (if (version == 2) 3 else 4) + 2
            if (size <= 0 || i + headerLen + size > tag.size) break
            val bodyStart = i + headerLen
            if (id == "USLT" || id == "ULT" || id == "SYLT" || id == "SLT") {
                parseLyricsFrame(tag, bodyStart, size)?.let { return it }
            }
            i += headerLen + size
        }
        return null
    }

    private fun parseLyricsFrame(tag: ByteArray, start: Int, size: Int): String? {
        if (size < 5) return null
        val encoding = tag[start].toInt() and 0xFF
        // 1 byte language + description (terminator depends on encoding).
        var pos = start + 1 + 3
        val charset: Charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1, 2 -> StandardCharsets.UTF_16
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }
        val terminator = if (encoding == 1 || encoding == 2) 2 else 1
        while (pos + terminator <= start + size) {
            if ((encoding == 1 || encoding == 2) &&
                tag[pos] == 0.toByte() && tag[pos + 1] == 0.toByte()
            ) {
                pos += 2
                break
            }
            if (encoding != 1 && encoding != 2 && tag[pos] == 0.toByte()) {
                pos += 1
                break
            }
            pos++
        }
        if (pos >= start + size) return null
        // Strip a UTF BOM if present.
        if (charset == StandardCharsets.UTF_16 && pos + 2 <= start + size) {
            val b0 = tag[pos].toInt() and 0xFF
            val b1 = tag[pos + 1].toInt() and 0xFF
            if ((b0 == 0xFE && b1 == 0xFF) || (b0 == 0xFF && b1 == 0xFE)) pos += 2
        }
        val raw = tag.copyOfRange(pos, start + size)
        val text = String(raw, charset)
        // SYLT frames contain language codes/time stamps; extract only text lines.
        val cleaned = text.replace("\u0000", "").trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun sizeOf(bytes: ByteArray, version: Int, extended: Boolean): Int {
        if (extended && version == 2) {
            return ((bytes[0].toInt() and 0xFF) shl 16) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                (bytes[2].toInt() and 0xFF)
        }
        if (version == 4 && !extended) {
            return ((bytes[0].toInt() and 0x7F) shl 21) or
                ((bytes[1].toInt() and 0x7F) shl 14) or
                ((bytes[2].toInt() and 0x7F) shl 7) or
                (bytes[3].toInt() and 0x7F)
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun skip(input: InputStream, count: Int) {
        var remaining = count
        val buffer = ByteArray(4096)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            remaining -= read
        }
    }
}
