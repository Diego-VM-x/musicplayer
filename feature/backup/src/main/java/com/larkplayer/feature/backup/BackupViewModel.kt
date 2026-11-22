package com.larkplayer.feature.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import com.larkplayer.feature.library.scanner.OriginalLibraryScanner
import android.os.Environment
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: BackupManager,
    private val originalScanner: OriginalLibraryScanner,
    private val lpbkImporter: LpbkImporter,
) : ViewModel() {
    suspend fun export(uri: Uri, includeSongs: Boolean): String =
        manager.export(uri, includeSongs).fold(
            onSuccess = { it.message },
            onFailure = { "Error al exportar: ${it.message.orEmpty()}" },
        )

    suspend fun import(uri: Uri): String =
        manager.import(uri).fold(
            onSuccess = { it.message },
            onFailure = { "Error al importar: ${it.message.orEmpty()}" },
        )

    /** Import one or more `.info` files from the original app. */
    suspend fun importOriginalFiles(uris: List<Uri>): String {
        var playlists = 0
        var songs = 0
        val errors = mutableListOf<String>()
        uris.forEach { uri ->
            manager.importOriginalInfo(uri).fold(
                onSuccess = {
                    playlists += it.restoredPlaylists
                    songs += it.restoredSongs
                },
                onFailure = { errors += it.message.orEmpty() },
            )
        }
        return if (playlists == 0) {
            "No se pudieron importar playlists: ${errors.firstOrNull().orEmpty()}"
        } else {
            "Importadas $playlists playlists y vinculadas $songs canciones" +
                if (errors.isNotEmpty()) " (${errors.size} archivos omitidos)" else ""
        }
    }

    suspend fun importLocalBackup(uri: Uri): String =
        lpbkImporter.import(uri).fold(
            onSuccess = {
                "Copia local importada: ${it.playlists} playlists, " +
                    "${it.linked} canciones vinculadas, ${it.unmatched} sin archivo local"
            },
            onFailure = { "Error al importar la copia local: ${it.message.orEmpty()}" },
        )

    /** Finds the newest `Backup_of_Lark_Player_*.lrc` for one-tap restore. */
    /** Finds a copied `vlc_database` in Downloads/Documents or app folders. */
    fun findOriginalDatabase(): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                    null, null,
                    "${MediaStore.Downloads.DATE_ADDED} DESC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)).orEmpty()
                        if (name.equals("vlc_database", true) || name.startsWith("vlc_database")) {
                            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                }
            }
        }
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(null),
        )
        return dirs.flatMap { dir ->
            runCatching {
                dir.walkTopDown().maxDepth(3).filter {
                    it.isFile && it.name.equals("vlc_database", true)
                }.toList()
            }.getOrDefault(emptyList())
        }.maxByOrNull { it.lastModified() }?.let { Uri.fromFile(it) }
    }

    fun findLocalBackup(): Uri? {
        // Professional detection: query MediaStore first (works without
        // storage permission and finds files in any Downloads location),
        // then fall back to well-known directories.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                    null, null,
                    "${MediaStore.Downloads.DATE_ADDED} DESC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)).orEmpty()
                        if (name.startsWith("Backup_of_Lark_Player_", true) && name.endsWith(".lrc", true)) {
                            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                }
            }
        }
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Documents/Lark Player"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lark Player"),
        )
        return dirs.flatMap { dir ->
            runCatching {
                dir.listFiles()?.filter {
                    it.isFile && it.name.startsWith("Backup_of_Lark_Player_", true) &&
                        it.name.endsWith(".lrc", true)
                }.orEmpty()
            }.getOrDefault(emptyList())
        }.maxByOrNull { it.lastModified() }?.let { Uri.fromFile(it) }
    }

    suspend fun importOriginalDatabase(uri: Uri): String =
        originalScanner.importFromSaf(uri).fold(
            onSuccess = {
                "Importadas ${it.playlists} playlists desde vlc_database: " +
                    "${it.linked} canciones vinculadas, ${it.unmatched} sin archivo local"
            },
            onFailure = { "Error al importar vlc_database: ${it.message.orEmpty()}" },
        )

    fun findOriginalFiles(): List<Uri> = manager.findOriginalInfoFiles()

    fun findAutoBackup(): Uri? = manager.findAutoBackup()
}
