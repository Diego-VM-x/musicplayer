package com.larkplayer.core.common

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Reads lyrics embedded in the audio file itself. No third-party tag library.
 *
 * This is the Android/Kotlin metadata reader used when MediaStore (and the
 * platform metadata retriever) does not expose the tag at all — which is the
 * usual case for USLT/SYLT and for custom Vorbis comment keys.
 *
 * Supported containers:
 *  - MP3 / ID3v2.2-2.4: USLT/ULT (plain) and SYLT/SLT (synchronized). SYLT
 *    entries are converted to enhanced LRC with `<mm:ss.xx>` word marks so the
 *    word-level karaoke survives the trip through Room (plain text column).
 *  - FLAC: Vorbis comment fields LYRICS / UNSYNCEDLYRICS / LYRICS_LRC.
 *  - OGG Vorbis / Opus: the Vorbis-comment packet (all occurrences are tried).
 *  - MP4/M4A: the ©lyr (0xA9 "lyr") ilst atom, UTF-8 and UTF-16 payloads.
 *
 * Every string is decoded through [LyricsTextUtils], so UTF-8, ISO-8859-1 and
 * UTF-16 (with or without BOM) all work and API 24 is supported.
 */
object EmbeddedLyrics {
    private const val MAX_TAG_BYTES = 4 * 1024 * 1024
    private const val MAX_SCAN_BYTES = 256 * 1024

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

    private fun parse(input: InputStream): String? =
        parse(PushbackInputStream(BufferedInputStream(input), 16), allowRescan = true)

    private fun parse(input: PushbackInputStream, allowRescan: Boolean): String? {
        val head = LyricsTextUtils.readFully(input, 12) ?: return null
        input.unread(head)
        return when {
            isId3(head, 0) -> parseId3(input)

            isFlac(head, 0) -> parseFlac(input)

            head.size >= 4 && head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
                head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte() -> parseOgg(input)

            isMp4(head, 0) -> parseMp4(input)

            // Some files carry junk, an APEv2 footer or a large cover before the
            // ID3 header. A bounded rescan recovers the tag the platform reader
            // silently misses.
            allowRescan -> rescanId3(input)

            else -> null
        }
    }

    private fun rescanId3(input: PushbackInputStream): String? {
        val prefix = LyricsTextUtils.readBytes(input, MAX_SCAN_BYTES)
        for (i in 0..prefix.size - 3) {
            if (isId3(prefix, i)) {
                parse(
                    PushbackInputStream(
                        BufferedInputStream(ByteArrayInputStream(prefix, i, prefix.size - i)), 16,
                    ),
                    false,
                )?.let { return it }
            }
        }
        return null
    }

    private fun isId3(bytes: ByteArray, at: Int): Boolean =
        at >= 0 && at + 3 <= bytes.size &&
            bytes[at] == 'I'.code.toByte() && bytes[at + 1] == 'D'.code.toByte() &&
            bytes[at + 2] == '3'.code.toByte()

    private fun isFlac(bytes: ByteArray, at: Int): Boolean =
        at >= 0 && at + 4 <= bytes.size &&
            bytes[at] == 'f'.code.toByte() && bytes[at + 1] == 'L'.code.toByte() &&
            bytes[at + 2] == 'a'.code.toByte() && bytes[at + 3] == 'C'.code.toByte()

    // ------------------------------------------------------------------ ID3

    private fun parseId3(input: InputStream): String? {
        val header = LyricsTextUtils.readFully(input, 10) ?: return null
        val version = header[3].toInt() and 0xFF
        if (version !in 2..4) return null
        val flags = header[5].toInt() and 0xFF
        val tagSize = synchsafe(header[6], header[7], header[8], header[9])
        if (tagSize <= 0 || tagSize > MAX_TAG_BYTES) return null
        var tag = LyricsTextUtils.readFully(input, tagSize.toInt()) ?: return null

        // ID3v2.4 unsynchronisation escapes 0xFF 0x00; without removing the
        // extra zero every frame after it is parsed at the wrong offset.
        if ((flags and 0x80) != 0) tag = unsynchronize(tag)

        var framePos = 0
        if ((flags and 0x40) != 0 && tag.size >= 4) {
            // v2.3 extended-header size includes its own 4 bytes; v2.4 does not.
            val extSize = if (version == 4) {
                synchsafe(tag[0], tag[1], tag[2], tag[3]).toInt()
            } else {
                LyricsTextUtils.be32(tag, 0).toInt()
            }
            framePos = (if (version == 4) 4 + extSize else extSize).coerceIn(0, tag.size)
        }

        val idLen = if (version == 2) 3 else 4
        val sizeLen = if (version == 2) 3 else 4
        // A file can carry both a plain USLT and a synchronized SYLT. Returning
        // the first frame (usually USLT) threw away real timestamps and made the
        // highlight drift from the audio.
        var plainLyrics: String? = null
        var syncedLyrics: String? = null
        var pos = framePos
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
            val bodyStart = pos + idLen + sizeLen + 2
            if (rawSize <= 0 || bodyStart + rawSize > tag.size) break

            var start = bodyStart
            var size = rawSize.toInt()
            if (version == 4) {
                val frameFlags = ((tag[bodyStart - 2].toInt() and 0xFF) shl 8) or
                    (tag[bodyStart - 1].toInt() and 0xFF)
                // Data-length-indicator: first 4 bytes of the body are a counter.
                if ((frameFlags and 0x0001) != 0 && size >= 4) {
                    start += 4
                    size -= 4
                }
            }

            if (id == "USLT" || id == "ULT" || id == "SYLT" || id == "SLT") {
                val synchronized = id.startsWith("S")
                val text = parseId3LyricsFrame(tag, start, size, synchronized)
                if (!text.isNullOrBlank()) {
                    if (synchronized) {
                        if (LyricsTextUtils.hasTimestamps(text)) syncedLyrics = text
                    } else if (plainLyrics == null) {
                        plainLyrics = text
                    }
                }
            }
            pos = bodyStart + rawSize.toInt()
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
        // encoding(1) + language(3); SYLT adds timestamp format + content type
        // before the description. The old code started at +5 for SYLT and ate
        // the content-type byte as part of the description.
        val descriptionStart = start + if (synchronized) 6 else 4
        var pos = findTerminator(tag, descriptionStart, end, width)?.plus(width) ?: return null
        if (width == 2 && pos + 1 < end) {
            val b0 = tag[pos].toInt() and 0xFF
            val b1 = tag[pos + 1].toInt() and 0xFF
            if ((b0 == 0xFE && b1 == 0xFF) || (b0 == 0xFF && b1 == 0xFE)) pos += 2
        }
        if (pos >= end) return null

        if (!synchronized) {
            return LyricsTextUtils.decode(tag.copyOfRange(pos, end), charset)
                .takeIf { it.isNotBlank() }
        }

        // SYLT: encoding, language, timestamp format, content type,
        // description<NUL>, then repeated <text<NUL><4-byte timestamp>.
        val timestampFormat = tag[start + 4].toInt() and 0xFF
        val entries = mutableListOf<Pair<Long, String>>()
        var p = pos
        while (p < end) {
            val separator = findTerminator(tag, p, end, width) ?: break
            val text = LyricsTextUtils.decode(tag.copyOfRange(p, separator), charset)
            p = separator + width
            if (p + 4 > end) break
            val stamp = LyricsTextUtils.be32(tag, p)
            p += 4
            // 1 = milliseconds, 2 = MPEG frames (~26 ms at 44.1 kHz/1152).
            val ms = if (timestampFormat == 2) stamp * 1152L * 1000L / 44100L else stamp
            if (text.isNotBlank()) entries += ms to text
        }
        if (entries.isEmpty()) {
            return LyricsTextUtils.decode(tag.copyOfRange(pos, end), charset)
                .takeIf { it.isNotBlank() }
        }
        return syltToLrc(entries.sortedBy { it.first })
    }

    /**
     * Groups SYLT fragments into singable lines and keeps every fragment as an
     * enhanced-LRC word mark. A gap, punctuation or a long line starts a new
     * line; consecutive words keep their exact individual timestamps.
     */
    private fun syltToLrc(entries: List<Pair<Long, String>>): String {
        val lines = mutableListOf<Pair<Long, String>>()
        var current = StringBuilder()
        var currentStart = 0L
        var words = 0
        var previousMs = Long.MIN_VALUE

        fun flush() {
            if (current.isNotBlank()) lines += currentStart to current.toString().trim()
            current = StringBuilder()
            words = 0
        }

        entries.forEach { (ms, raw) ->
            val token = raw.trim()
            if (token.isEmpty()) return@forEach
            val gap = if (previousMs == Long.MIN_VALUE) 0L else ms - previousMs
            val endsPhrase = token.lastOrNull()?.let {
                it == '.' || it == '!' || it == '?' || it == '…' || it == ';' || it == ':'
            } ?: false
            if (current.isNotEmpty() && (gap > 1_200L || words >= 10 || current.length > 90 || endsPhrase)) {
                flush()
            }
            if (current.isEmpty()) currentStart = ms
            // Literal '<' / '>' would be mistaken for enhanced-LRC marks.
            val safe = token.replace('<', '(').replace('>', ')')
            current.append('<').append(lrcTime(ms)).append('>').append(safe).append(' ')
            words++
            previousMs = ms
        }
        flush()
        return lines.joinToString("\n") { "[${lrcTime(it.first)}]${it.second}" }
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

    private fun unsynchronize(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            out.write(b)
            if (b == 0xFF && i + 1 < data.size && data[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    // ----------------------------------------------------------------- FLAC

    private fun parseFlac(input: InputStream): String? {
        val head = LyricsTextUtils.readFully(input, 4) ?: return null
        if (!isFlac(head, 0)) return null
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
            val entry = LyricsTextUtils.decode(
                block.copyOfRange(pos, pos + len), StandardCharsets.UTF_8,
            )
            pos += len
            val eq = entry.indexOf('=')
            if (eq <= 0) return@repeat
            val key = entry.substring(0, eq).trim().uppercase(Locale.US)
            if (key in LYRIC_KEYS) {
                entry.substring(eq + 1).takeIf { it.isNotBlank() }?.let { found += it }
            }
        }
        return found.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private val LYRIC_KEYS = setOf(
        "LYRICS", "UNSYNCEDLYRICS", "LYRICS_LRC", "LYRICS_SYLT", "SYNCEDLYRICS",
        "LYRIC", "SYLT",
    )

    // ------------------------------------------------------------------ OGG

    private fun parseOgg(input: InputStream): String? {
        val data = LyricsTextUtils.readBytes(input, 512 * 1024)
        val patterns = listOf(
            "\u0003vorbis".toByteArray(StandardCharsets.ISO_8859_1),
            "OpusTags".toByteArray(StandardCharsets.US_ASCII),
        )
        // The first "\x03vorbis" is the identification packet, not the comment
        // packet. Try every occurrence until the Vorbis-comment layout parses.
        for (pattern in patterns) {
            var from = 0
            while (true) {
                val index = indexOf(data, pattern, from) ?: break
                parseVorbisComment(data, index + pattern.size)?.let { return it }
                from = index + 1
            }
        }
        return null
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int? {
        if (needle.isEmpty() || needle.size > haystack.size) return null
        for (i in from.coerceAtLeast(0)..haystack.size - needle.size) {
            var matches = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    matches = false
                    break
                }
            }
            if (matches) return i
        }
        return null
    }

    // --------------------------------------------------------------- MP4/M4A

    private fun isMp4(bytes: ByteArray, at: Int): Boolean =
        at >= 0 && at + 12 <= bytes.size &&
            bytes[at + 4] == 'f'.code.toByte() && bytes[at + 5] == 't'.code.toByte() &&
            bytes[at + 6] == 'y'.code.toByte() && bytes[at + 7] == 'p'.code.toByte()

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
        while (pos + 16 <= end) {
            val size = LyricsTextUtils.be32(data, pos).toInt()
            val type = String(data, pos + 4, 4, StandardCharsets.ISO_8859_1)
            if (size <= 16 || pos + size > end) return null
            if (type == "data") {
                // version(1) + flags(3) where flags = data type (1 UTF-8, 2 UTF-16).
                val dataType = ((data[pos + 9].toInt() and 0xFF) shl 16) or
                    ((data[pos + 10].toInt() and 0xFF) shl 8) or (data[pos + 11].toInt() and 0xFF)
                val charset = when (dataType) {
                    1 -> StandardCharsets.UTF_8
                    2 -> StandardCharsets.UTF_16BE
                    else -> null
                }
                val textStart = pos + 16
                return LyricsTextUtils.decode(
                    data.copyOfRange(textStart, pos + size), charset,
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
