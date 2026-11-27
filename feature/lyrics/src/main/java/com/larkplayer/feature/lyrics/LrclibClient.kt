package com.larkplayer.feature.lyrics

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Free lyrics API client (LRCLIB): no key, no account, HTTPS only.
 * Returns the raw LRC text so [LrcParser] stays the single source of timing logic.
 */
internal object LrclibClient {
    private const val ENDPOINT = "https://lrclib.net/api/get"
    private const val TIMEOUT_MS = 8_000

    fun buildUrl(artist: String, title: String, durationMs: Long): String = buildString {
        append(ENDPOINT)
        append("?artist=").append(encode(artist))
        append("&track_name=").append(encode(title))
        if (durationMs > 0) append("&duration=").append(durationMs / 1_000L)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Prefers time-synced LRC; falls back to plain text so the UI can show it untimed. */
    fun parseSynced(body: String): String? = runCatching {
        val json = JsonParser.parseString(body).asJsonObject
        val synced = json.get("syncedLyrics")?.takeIf { !it.isJsonNull }?.asString
            ?.takeIf { it.isNotBlank() }
        val plain = json.get("plainLyrics")?.takeIf { !it.isJsonNull }?.asString
            ?.takeIf { it.isNotBlank() }
        synced ?: plain
    }.getOrNull()

    suspend fun fetch(artist: String, title: String, durationMs: Long): String? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank() && title.isBlank()) return@withContext null
            runCatching {
                val connection = (URL(buildUrl(artist, title, durationMs)).openConnection() as HttpsURLConnection)
                    .apply {
                        requestMethod = "GET"
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "LarkClean-Lyrics/1.0")
                    }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.bufferedReader().use { parseSynced(it.readText()) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
}
