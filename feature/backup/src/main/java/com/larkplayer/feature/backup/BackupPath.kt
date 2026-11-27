package com.larkplayer.feature.backup

import java.io.File

/** Pure helpers that keep ZIP entry names inside the restore directory. */
internal object BackupPath {
    /**
     * A ZIP entry such as `Music/../../sdcard/evil.mp3` must be rejected: the
     * import used to feed the raw suffix to `File(dir, name)`, which is a
     * Zip Slip write outside the app's restore folder.
     */
    fun safeMusicFileName(entryName: String): String? {
        if (!entryName.startsWith("Music/")) return null
        val name = entryName.removePrefix("Music/")
        if (name.isBlank() || name == "." || name == "..") return null
        if (name.contains('/') || name.contains('\\') || name.startsWith('/')) return null
        if (File(name).isAbsolute) return null
        return name
    }

    /** Avoids duplicate `ZipEntry` names when two songs share a file name. */
    fun musicEntry(songId: Long, path: String, used: MutableSet<String>): String {
        val base = File(path).name.replace('/', '_').replace('\\', '_')
            .takeIf { it.isNotBlank() } ?: "song_$songId"
        var name: String
        if (used.add(base)) {
            name = base
        } else {
            name = "${songId}-$base"
            var suffix = 2
            while (!used.add(name)) {
                name = "${songId}-$base-$suffix"
                suffix++
            }
        }
        return "Music/$name"
    }
}
