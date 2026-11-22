package com.larkplayer.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.larkplayer.feature.library.scanner.OriginalLibraryScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: BackupManager,
    private val originalScanner: OriginalLibraryScanner,
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
