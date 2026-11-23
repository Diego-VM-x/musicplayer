package com.larkplayer.core.common

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Reads lyrics embedded in the audio file itself. No third-party tag library.
 *
 * Supported containers:
 *  - MP3 / ID3v2.2-2.4: USLT/ULT (plain) and SYLT/SLT (synchronized, converted
 *    to LRC timestamps so the rest of the app can parse it uniformly).
 *  - FLAC: Vorbis comment fields LYRICS / UNSYNCEDLYRICS / LYRICS_LRC.
 *  - OGG Vorbis / Opus: the Vorbis-comment packet.
 *  - MP4/M4A: the ©lyr (0xA9 "lyr") ilst atom.
 *
 * Every string is decoded through [LyricsTextUtils], so UTF-8, ISO-8859-1 and
 * UTF-16 (with or without BOM) all work and API 24 is supported.
 */
object EmbeddedLyrics {
    private const val MAX_TAG_BYTES = 4 * 1024 * 1024

    fun read(context: Context, song: com.larkplayer.core.model.Song): String? {
        val uris = listOfNotNull(
            song.contentUri.takeIf { song.id > 0 },
            "content://media/external/audio/media/${song.id}".takeIf { song.id > 0 },
        )
        for (uri in uris) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { parse(it) }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return readFile(song.path)
    }

    /** Compatibility overload used by the player feature. */
    fun read(context: Context, songPath: String, songId: Long): String? {
        if (songId > 0) {
            val uri = "content://media/external/audio/media/$songId"
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { parse(it) }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return readFile(songPath)
    }

    private fun readFile(path: String): String? {
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.isFile) return@runCatching null
            file.inputStream().use { parse(it) }
        }.getOrNull()
    }

    private fun parse(input: InputStream): String? {
        val push = PushbackInputStream(BufferedInputStream(input), 16)
        val head = LyricsTextUtils.readFully(push, 12) ?: return null
        push.unread(head)
        return when {
            head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() &&
                head[2] == '3'.code.toByte() -> parseId3(push)

            head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() &&
                head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() -> parseFlac(push)

            head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
                head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte() -> parseOgg(push)

            isMp4(head) -> parseMp4(push)

            else -> null
        }
    }

    // ------------------------------------------------------------------ ID3

    private fun parseId3(input: InputStream): String? {
        val header = LyricsTextUtils.readFully(input, 10) ?: return null
        val version = header[3].toInt() and 0xFF
        if (version !in 2..4) return null
        val tagSize = synchsafe(header[6], header[7], header[8], header[9])
        if (tagSize <= 0 || tagSize > MAX_TAG_BYTES) return null
        // Read exactly the tag; with no extended header handling in the stream
        // we avoid the off-by-one that broke tags carrying an extended header.
        val tag = LyricsTextUtils.readFully(input, tagSize.toInt()) ?: return null

        val idLen = if (version == 2) 3 else 4
        val sizeLen = if (version == 2) 3 else 4
        // A file can carry both a plain USLT and a synchronized SYLT. The old
        // code returned the first frame it found (usually USLT), throwing away
        // real timestamps and making the highlight drift from the audio.
        var plainLyrics: String? = null
        var syncedLyrics: String? = null
        var pos = 0
        while (pos + idLen + sizeLen + 2 <= tag.size) {
            val id = String(tag, pos, idLen, StandardCharsets.ISO_8859_1)
                .trim('\u0000').uppercase(Locale.US)
            val rawSize = when {
                version == 2 -> be3(tag, pos + idLen)
                version == 4 -> synchsafe(
                    tag[pos + idLen], tag[pos + idLen + 1],
                    tag[pos + idLen + 2], tag[pos + idLen + 3],
                )

                else -> LyricsTextUtils.be32(tag, pos + idLen)
            }
            val frameStart = pos + idLen + sizeLen + 2
            if (rawSize <= 0 || frameStart + rawSize > tag.size) break

            var bodyStart = frameStart
            var bodySize = rawSize.toInt()
            if (version == 4) {
                val flags = ((tag[frameStart - 2].toInt() and 0xFF) shl 8) or
                    (tag[frameStart - 1].toInt() and 0xFF)
                // Data-length-indicator: first 4 bytes of the body are a counter.
                if ((flags and 0x0001) != 0 && bodySize >= 4) {
                    bodyStart += 4
                    bodySize -= 4
                }
            }

            if (id == "USLT" || id == "ULT" || id == "SYLT" || id == "SLT") {
                val text = parseId3LyricsFrame(tag, bodyStart, bodySize, id.startsWith("S"))
                if (!text.isNullOrBlank()) {
                    if (id.startsWith("S")) {
                        if (LyricsTextUtils.hasTimestamps(text)) syncedLyrics = text
                    } else if (plainLyrics == null) {
                        plainLyrics = text
                    }
                }
            }
            pos = frameStart + rawSize.toInt()
        }
        return syncedLyrics ?: plainLyrics
    }

    private fun parseId3LyricsFrame(
        tag: ByteArray,
        start: Int,
        size: Int,
        synchronized: Boolean,
    ): String? {
        if (size < 6 || start + size > tag.size) return null
        val encoding = tag[start].toInt() and 0xFF
        // 0 = ISO-8859-1, 1 = UTF-16LE (BOM), 2 = UTF-16BE, 3 = UTF-8.
        val charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_16LE
        }
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        val end = start + size
        // encoding(1) + language(3), then description terminated by a NUL of
        // the frame's character width.
        var pos = findTerminator(tag, start + 4, end, width) ?: return null
        pos += width
        if (pos + width <= end) {
            val b0 = tag[pos].toInt() and 0xFF
            val b1 = tag[pos + 1].toInt() and 0xFF
            if ((b0 == 0xFE && b1 == 0xFF) || (b0 == 0xFF && b1 == 0xFE)) pos += 2
        }
        if (pos >= end) return null

        if (!synchronized) {
            return LyricsTextUtils.decode(tag.copyOfRange(pos, end)).takeIf { it.isNotBlank() }
        }

        // SYLT: encoding, language, timestamp format, content type,
        // description<NUL>, then repeated <text<NUL><4-byte timestamp>.
        val timestampFormat = tag[start + 4].toInt() and 0xFF
        var p = findTerminator(tag, start + 5, end, width)?.plus(width) ?: return null
        val entries = mutableListOf<Pair<Long, String>>()
        while (p < end) {
            val separator = findTerminator(tag, p, end, width) ?: break
            val text = LyricsTextUtils.decode(tag.copyOfRange(p, separator))
            p = separator + width
            if (p + 4 > end) break
            val stamp = LyricsTextUtils.be32(tag, p)
            p += 4
            // 1 = milliseconds, 2 = MPEG frames (~26 ms at 44.1 kHz/1152).
            val ms = if (timestampFormat == 2) stamp * 1152L * 1000L / 44100L else stamp
            if (text.isNotBlank()) entries += ms to text
        }
        if (entries.isEmpty()) {
            return LyricsTextUtils.decode(tag.copyOfRange(pos, end)).takeIf { it.isNotBlank() }
        }
        return entries.sortedBy { it.first }
            .joinToString("\n") { "[${lrcTime(it.first)}]${it.second}" }
    }

    private fun findTerminator(tag: ByteArray, from: Int, end: Int, width: Int): Int? {
        var i = from
        while (i < end) {
            if (width == 1) {
                if (tag[i] == 0.toByte()) return i
            } else if (tag[i] == 0.toByte() && i + 1 < end && tag[i + 1] == 0.toByte()) {
                return i
            }
            i += width
        }
        return null
    }

    private fun lrcTime(ms: Long): String {
        val total = maxOf(0L, ms) / 1000
        return String.format(
            Locale.US, "%02d:%02d.%02d", total / 60, total % 60, (maxOf(0L, ms) % 1000) / 10,
        )
    }

    // ----------------------------------------------------------------- FLAC

    private fun parseFlac(input: InputStream): String? {
        val head = LyricsTextUtils.readFully(input, 4) ?: return null
        if (head[0] != 'f'.code.toByte() || head[1] != 'L'.code.toByte() ||
            head[2] != 'a'.code.toByte() || head[3] != 'C'.code.toByte()
        ) return null
        while (true) {
            val blockHeader = LyricsTextUtils.readFully(input, 4) ?: return null
            val last = (blockHeader[0].toInt() and 0x80) != 0
            val type = blockHeader[0].toInt() and 0x7F
            val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                ((blockHeader[2].toInt() and 0xFF) shl 8) or (blockHeader[3].toInt() and 0xFF)
            if (length <= 0 || length > MAX_TAG_BYTES) return null
            val block = LyricsTextUtils.readFully(input, length) ?: return null
            if (type == 4) {
                parseVorbisComment(block)?.let { return it }
            }
            if (last) break
        }
        return null
    }

    /** Vorbis comment payload: vendor length + vendor + count + length/value pairs. */
    private fun parseVorbisComment(block: ByteArray, offset: Int = 0): String? {
        if (offset + 4 > block.size) return null
        var pos = offset
        val vendorLen = LyricsTextUtils.le32(block, pos).toInt()
        pos += 4
        if (vendorLen < 0 || pos + vendorLen > block.size) return null
        pos += vendorLen
        if (pos + 4 > block.size) return null
        val count = LyricsTextUtils.le32(block, pos).toInt().coerceAtMost(512)
        pos += 4
        val found = mutableListOf<String>()
        repeat(count) {
            if (pos + 4 > block.size) return@repeat
            val len = LyricsTextUtils.le32(block, pos).toInt()
            pos += 4
            if (len < 0 || pos + len > block.size) return@repeat
            val entry = String(block, pos, len, StandardCharsets.UTF_8)
            pos += len
            val eq = entry.indexOf('=')
            if (eq <= 0) return@repeat
            val key = entry.substring(0, eq).trim().uppercase(Locale.US)
            if (key in LYRIC_KEYS) {
                LyricsTextUtils.clean(entry.substring(eq + 1)).takeIf { it.isNotBlank() }
                    ?.let { found += it }
            }
        }
        return found.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private val LYRIC_KEYS = setOf(
        "LYRICS", "UNSYNCEDLYRICS", "LYRICS_LRC", "LYRICS_SYLT", "LYRIC", "SYLT",
    )

    // ------------------------------------------------------------------ OGG

    private fun parseOgg(input: InputStream): String? {
        val data = LyricsTextUtils.readBytes(input, 512 * 1024)
        val vorbis = bytePattern("\u0003vorbis".toByteArray(StandardCharsets.ISO_8859_1))
        val opus = bytePattern("OpusTags".toByteArray(StandardCharsets.US_ASCII))
        val index = indexOf(data, vorbis)?.let { it + vorbis.size }
            ?: indexOf(data, opus)?.let { it + opus.size }
            ?: return null
        return parseVorbisComment(data, index)
    }

    private fun bytePattern(value: ByteArray): ByteArray = value

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int? {
        if (needle.isEmpty() || needle.size > haystack.size) return null
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }

    // --------------------------------------------------------------- MP4/M4A

    private fun isMp4(head: ByteArray): Boolean =
        head.size >= 12 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()

    private fun parseMp4(input: InputStream): String? {
        val data = LyricsTextUtils.readBytes(input, MAX_TAG_BYTES)
        return findMp4Lyrics(data, 0, data.size, 0)
    }

    private fun findMp4Lyrics(data: ByteArray, from: Int, end: Int, depth: Int): String? {
        if (depth > 8) return null
        var pos = from
        while (pos + 8 <= end) {
            var size = LyricsTextUtils.be32(data, pos)
            var header = 8
            if (size == 1L) {
                if (pos + 16 > end) return null
                size = ((LyricsTextUtils.be32(data, pos + 8) shl 32) or
                    LyricsTextUtils.be32(data, pos + 12))
                header = 16
            }
            if (size == 0L) size = (end - pos).toLong()
            if (size < header || pos + size > end) return null
            val type = String(data, pos + 4, 4, StandardCharsets.ISO_8859_1)
            val body = pos + header
            val bodyEnd = (pos + size).toInt()

            if (type == "\u00a9lyr" || type == "\u00a9LYR") {
                parseMp4DataAtom(data, body, bodyEnd)?.let { return it }
            } else if (type in MP4_CONTAINERS) {
                val childStart = if (type == "meta" && body + 4 <= bodyEnd) body + 4 else body
                findMp4Lyrics(data, childStart, bodyEnd, depth + 1)?.let { return it }
            }
            pos = bodyEnd
        }
        return null
    }

    private val MP4_CONTAINERS = setOf("moov", "udta", "meta", "ilst", "trak", "mdia")

    private fun parseMp4DataAtom(data: ByteArray, from: Int, end: Int): String? {
        var pos = from
        while (pos + 8 <= end) {
            val size = LyricsTextUtils.be32(data, pos).toInt()
            val type = String(data, pos + 4, 4, StandardCharsets.ISO_8859_1)
            if (size <= 8 || pos + size > end) return null
            if (type == "data") {
                val textStart = pos + 8 + 8
                if (textStart >= pos + size) return null
                return LyricsTextUtils.decode(
                    data.copyOfRange(textStart, pos + size),
                ).takeIf { it.isNotBlank() }
            }
            pos += size
        }
        return null
    }

    // ----------------------------------------------------------------- utils

    private fun be3(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            (bytes[offset + 2].toLong() and 0xFF)

    private fun synchsafe(a: Byte, b: Byte, c: Byte, d: Byte): Long =
        ((a.toLong() and 0x7F) shl 21) or
            ((b.toLong() and 0x7F) shl 14) or
            ((c.toLong() and 0x7F) shl 7) or
            (d.toLong() and 0x7F)
}
