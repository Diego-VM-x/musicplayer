package com.larkplayer.feature.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

data class DetectedBackup(
    val uri: Uri,
    val displayName: String,
    val type: String, // "Copia local (.lrc)", "Respaldo Drive (.info/.json)", "Copia ZIP (.zip)"
    val dateModified: Long,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: BackupManager,
) : ViewModel() {

    private val _lastErrorLog = MutableStateFlow<String?>(null)
    val lastErrorLog: StateFlow<String?> = _lastErrorLog.asStateFlow()

    fun clearErrorLog() {
        _lastErrorLog.value = null
    }

    suspend fun importBackup(uri: Uri): String {
        _lastErrorLog.value = null
        val result = manager.importUniversal(uri)
        return result.fold(
            onSuccess = { res ->
                val errorDetails = if (res.errors.isNotEmpty()) {
                    "\n\nAvisos:\n" + res.errors.joinToString("\n")
                } else ""
                res.message + errorDetails
            },
            onFailure = { err ->
                val simplified = simplifyError(err)
                _lastErrorLog.value = simplified
                "Error al importar: ${err.message ?: "Formato no compatible"}"
            }
        )
    }

    suspend fun exportZip(uri: Uri, includeSongs: Boolean): String {
        _lastErrorLog.value = null
        return manager.export(uri, includeSongs).fold(
            onSuccess = { it.message },
            onFailure = { err ->
                val simplified = simplifyError(err)
                _lastErrorLog.value = simplified
                "Error al exportar: ${err.message ?: "Error desconocido"}"
            }
        )
    }

    suspend fun exportDriveInfo(uri: Uri): String {
        _lastErrorLog.value = null
        return manager.exportOriginalDriveInfo(uri).fold(
            onSuccess = { it.message },
            onFailure = { err ->
                val simplified = simplifyError(err)
                _lastErrorLog.value = simplified
                "Error al exportar: ${err.message ?: "Error desconocido"}"
            }
        )
    }

    private fun simplifyError(throwable: Throwable): String {
        val root = getRootCause(throwable)
        val stackLine = root.stackTrace.firstOrNull { it.className.startsWith("com.larkplayer") }
            ?: root.stackTrace.firstOrNull()
        val loc = if (stackLine != null) " en ${stackLine.fileName}:${stackLine.lineNumber}" else ""
        return "${root.javaClass.simpleName}: ${root.message.orEmpty()}$loc"
    }

    private fun getRootCause(throwable: Throwable): Throwable {
        var cause: Throwable = throwable
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause
    }

    /**
     * Finds the most relevant backup files automatically across Downloads, Documents and app storage.
     * Detects LPBK (.lrc), Google Drive (.info / .json) and standard backups (.zip).
     */
    fun findAvailableBackups(): List<DetectedBackup> {
        val list = mutableListOf<DetectedBackup>()

        // 1. MediaStore Downloads & Files query on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collections = listOf(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Files.getContentUri("external"),
            )
            collections.forEach { collectionUri ->
                runCatching {
                    context.contentResolver.query(
                        collectionUri,
                        arrayOf(
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.DATE_MODIFIED
                        ),
                        null, null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val modCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                        while (c.moveToNext()) {
                            val name = c.getString(nameCol).orEmpty()
                            val id = c.getLong(idCol)
                            val mod = c.getLong(modCol) * 1000L
                            val uri = ContentUris.withAppendedId(collectionUri, id)

                            val type = classifyFileName(name)
                            if (type != null) {
                                if (list.none { it.displayName.equals(name, ignoreCase = true) }) {
                                    list.add(DetectedBackup(uri, name, type, mod))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Direct directory scan fallback
        val rootDir = Environment.getExternalStorageDirectory()
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(rootDir, "Download"),
            File(rootDir, "Downloads"),
            File(rootDir, "Documents"),
            File(rootDir, "Documents/Lark Player"),
            File(rootDir, "Lark Player"),
            File(rootDir, "Android/data/com.dywx.larkplayer/files"),
            File(rootDir, "Android/data/com.dywx.larkplayer/files/backup"),
            File(rootDir, "Android/data/com.dywx.larkplayer/files/Lark Player"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lark Player"),
            context.getExternalFilesDir(null),
            File(context.filesDir, "backups"),
        )

        dirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                runCatching {
                    dir.walkTopDown().maxDepth(4).filter { it.isFile }.forEach { file ->
                        val type = classifyFileName(file.name)
                        if (type != null) {
                            val uri = Uri.fromFile(file)
                            if (list.none { it.displayName.equals(file.name, ignoreCase = true) }) {
                                list.add(DetectedBackup(uri, file.name, type, file.lastModified()))
                            }
                        }
                    }
                }
            }
        }

        return list.sortedByDescending { it.dateModified }
    }

    private fun classifyFileName(name: String): String? {
        val lower = name.lowercase().trim()
        return when {
            lower.endsWith(".lrc") -> "Copia local Lark (.lrc)"
            lower.contains("playlists you backed up in lark player") -> "Respaldo Drive Lark (.info)"
            lower.endsWith(".info") -> "Respaldo Drive Lark (.info)"
            lower.endsWith(".json") && (lower.contains("playlist") || lower.contains("lark") || lower.contains("backup")) ->
                "Respaldo Drive / JSON (.json)"
            lower.contains("lark-backup") && lower.endsWith(".zip") -> "Copia LarkClean (.zip)"
            lower == "vlc_database" || lower.startsWith("vlc_database") -> "Base de datos Lark"
            lower.endsWith(".zip") && (lower.contains("lark") || lower.contains("backup")) -> "Copia de respaldo (.zip)"
            else -> null
        }
    }
}
