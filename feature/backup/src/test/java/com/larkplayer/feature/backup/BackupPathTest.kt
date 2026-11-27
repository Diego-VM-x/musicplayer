package com.larkplayer.feature.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A crafted ZIP entry must never escape the restore directory (Zip Slip). */
class BackupPathTest {

    @Test
    fun `traversal entry is rejected`() {
        assertNull(BackupPath.safeMusicFileName("Music/../../sdcard/evil.mp3"))
    }

    @Test
    fun `absolute entry is rejected`() {
        assertNull(BackupPath.safeMusicFileName("Music//etc/passwd"))
    }

    @Test
    fun `windows separator is rejected`() {
        assertNull(BackupPath.safeMusicFileName("Music/..\\secret.mp3"))
    }

    @Test
    fun `plain file name is accepted`() {
        assertEquals("song.mp3", BackupPath.safeMusicFileName("Music/song.mp3"))
    }

    @Test
    fun `duplicate exported names get a unique song id prefix`() {
        val used = mutableSetOf<String>()
        assertEquals("Music/song.mp3", BackupPath.musicEntry(1L, "/a/song.mp3", used))
        assertEquals("Music/2-song.mp3", BackupPath.musicEntry(2L, "/b/song.mp3", used))
    }
}
