package com.larkplayer.core.common

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Shared decoding / cleaning for every lyrics source (embedded tags, .lrc, .txt).
 *
 * The old code used `File.readText()` (UTF-8 only) and `InputStream.readNBytes()`
 * (API 33+), which made lyrics fail on ISO-8859-1/UTF-16 files and on devices
 * below Android 13. Everything here works from API 24.
 */
object LyricsTextUtils {
    const val MAX_BYTES: Int = 4 * 1024 * 1024

    /** Reads at most [limit] bytes without using the API 33 `readNBytes` helper. */
    fun readBytes(input: InputStream, limit: Int = MAX_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    /** Reads exactly [count] bytes, or null on EOF. */
    fun readFully(input: InputStream, count: Int): ByteArray? {
        if (count < 0) return null
        val out = ByteArray(count)
        var total = 0
        while (total < count) {
            val n = input.read(out, total, count - total)
            if (n < 0) break
            total += n
        }
        return if (total == count) out else null
    }

    /** UTF-8 / UTF-16 with BOM, strict UTF-8, then ISO-8859-1 as last resort. */
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val text = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)

            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)

            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)

            else -> runCatching {
                val decoder = StandardCharsets.UTF_8.newDecoder()
                decoder.onMalformedInput(CodingErrorAction.REPORT)
                decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            }.getOrElse { String(bytes, StandardCharsets.ISO_8859_1) }
        }
        return clean(text)
    }

    /**
     * Normalizes CRLF/CR to LF, drops BOM/zero-width/control garbage and trims
     * each line. Spanish accents and any other letters are preserved.
     */
    fun clean(raw: String): String {
        if (raw.isEmpty()) return ""
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val sb = StringBuilder(normalized.length)
        normalized.forEach { ch ->
            val code = ch.code
            val skip = when (code) {
                0xFEFF, 0xFFFD, 0x200B, 0x200E, 0x200F, 0x00AD -> true
                else -> code < 0x20 && ch != '\n' && ch != '\t'
            }
            if (!skip) sb.append(ch)
        }
        return sb.lines()
            .map { it.trim() }
            .dropWhile { it.isEmpty() }
            .dropLastWhile { it.isEmpty() }
            .joinToString("\n")
    }

    fun readFile(file: File): String? = runCatching {
        if (!file.isFile || file.length() > MAX_BYTES.toLong()) return@runCatching null
        file.inputStream().use { decode(readBytes(it)) }.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun readUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { decode(readBytes(it)) }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * True when the text contains real `[mm:ss.xx]` time tags. Used to prefer
     * synchronized sources (SYLT / .lrc) over plain USLT / .txt lyrics: plain
     * text can only be distributed proportionally, which never matches the
     * actual singing and made the highlight look out of order.
     */
    fun hasTimestamps(text: String): Boolean =
        Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\]""").containsMatchIn(text)

    /** Big-endian 32-bit helper used by the tag parsers. */
    fun be32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0L
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    /** Little-endian 32-bit helper used by Vorbis comments. */
    fun le32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0L
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}
