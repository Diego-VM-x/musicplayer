package com.larkplayer.feature.library.scanner

import com.larkplayer.core.database.entity.SongEntity
import com.larkplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the library resync path. A failed MediaStore query must
 * never wipe the local database, and a successful one must not throw away the
 * lyrics cache that was filled from sibling .lrc files.
 */
class LibrarySyncTest {

    private fun entity(id: Long, lyrics: String? = null) = SongEntity(
        id = id,
        title = "Old $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 100L,
        path = "/music/$id.mp3",
        lyricsText = lyrics,
    )

    private fun song(id: Long, title: String = "New $id") = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 100L,
        path = "/music/$id.mp3",
    )

    @Test
    fun `failed MediaStore query never replaces the library`() {
        val result = LibrarySync.merge(
            existing = listOf(entity(1)),
            fresh = emptyList(),
            querySucceeded = false,
        )

        assertFalse(result.shouldReplace)
        assertTrue(result.songs.isEmpty())
    }

    @Test
    fun `successful rescan preserves cached lyrics for unchanged songs`() {
        val result = LibrarySync.merge(
            existing = listOf(entity(1, lyrics = "[00:01.00]hello")),
            fresh = listOf(song(1, title = "Renamed")),
            querySucceeded = true,
        )

        assertTrue(result.shouldReplace)
        assertEquals(1, result.songs.size)
        assertEquals("Renamed", result.songs[0].title)
        assertEquals("[00:01.00]hello", result.songs[0].lyricsText)
    }

    @Test
    fun `successful rescan drops songs no longer present on the device`() {
        val result = LibrarySync.merge(
            existing = listOf(entity(1), entity(2)),
            fresh = listOf(song(1)),
            querySucceeded = true,
        )

        assertTrue(result.shouldReplace)
        assertEquals(listOf(1L), result.songs.map { it.id })
    }

    @Test
    fun `duplicate MediaStore rows are collapsed by id`() {
        val result = LibrarySync.merge(
            existing = emptyList(),
            fresh = listOf(song(7), song(7)),
            querySucceeded = true,
        )

        assertEquals(1, result.songs.size)
        assertEquals(7L, result.songs[0].id)
    }
}
