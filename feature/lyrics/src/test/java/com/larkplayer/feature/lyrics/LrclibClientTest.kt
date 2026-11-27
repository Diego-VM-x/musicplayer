package com.larkplayer.feature.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Internet lyrics: query encoding and response parsing must be deterministic. */
class LrclibClientTest {

    @Test
    fun `query encodes artist and title`() {
        val url = LrclibClient.buildUrl("Björk", "Human Behaviour", 0L)
        assertEquals(
            "https://lrclib.net/api/get?artist=Bj%C3%B6rk&track_name=Human+Behaviour",
            url,
        )
    }

    @Test
    fun `duration is sent in seconds`() {
        val url = LrclibClient.buildUrl("A", "B", 243_000L)
        assertEquals("https://lrclib.net/api/get?artist=A&track_name=B&duration=243", url)
    }

    @Test
    fun `synced lyrics win over plain lyrics`() {
        val json = """{"syncedLyrics":"[00:01.00]uno","plainLyrics":"uno"}"""
        assertEquals("[00:01.00]uno", LrclibClient.parseSynced(json))
    }

    @Test
    fun `plain lyrics are used when no timing exists`() {
        val json = """{"syncedLyrics":null,"plainLyrics":"sin tiempo"}"""
        assertEquals("sin tiempo", LrclibClient.parseSynced(json))
    }

    @Test
    fun `malformed response is ignored`() {
        assertNull(LrclibClient.parseSynced("<html>rate limited</html>"))
    }
}
