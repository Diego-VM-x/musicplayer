package com.larkplayer.feature.library.scanner

import com.larkplayer.core.database.entity.SongEntity
import com.larkplayer.core.model.Song

/** Pure logic for merging a MediaStore scan into the persisted library. */
internal object LibrarySync {
    data class Result(
        val shouldReplace: Boolean,
        val songs: List<SongEntity>,
    )

    /**
     * A failed query (for example `SecurityException` when the permission was
     * revoked) must leave the database untouched: an empty result is not
     * evidence that the user's music disappeared. On a successful query the
     * cached lyrics are carried over by song id so a rescan never throws away
     * text that was read from a sibling `.lrc` file.
     */
    fun merge(
        existing: List<SongEntity>,
        fresh: List<Song>,
        querySucceeded: Boolean,
    ): Result {
        if (!querySucceeded) return Result(false, emptyList())

        val cachedLyrics = existing.associateBy({ it.id }, { it.lyricsText })
        val songs = fresh.distinctBy { it.id }.map { song ->
            song.toEntity(lyricsText = cachedLyrics[song.id])
        }
        return Result(true, songs)
    }

    private fun Song.toEntity(lyricsText: String? = null) = SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        path = path,
        isVideo = isVideo,
        dateAdded = dateAdded,
        lyricsText = lyricsText,
    )
}
