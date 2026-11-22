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

    suspend fun importDriveFolder(treeUri: Uri): String {
        _lastErrorLog.value = null
        return runCatching {
            val resolver = context.contentResolver
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            data class ChildDoc(val uri: Uri, val name: String)
            val children = mutableListOf<ChildDoc>()

            resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idCol)
                    val displayName = cursor.getString(nameCol).orEmpty()
                    val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    children.add(ChildDoc(childUri, displayName))
                }
            }

            if (children.isEmpty()) error("La carpeta seleccionada está vacía o no tiene permisos de lectura")

            // Prioridad:
            // 1. Archivo de Playlists exacto ('Playlists you backed up in Lark Player')
            // 2. Cualquier archivo .info con 'playlist'
            // 3. Cualquier archivo .info
            val target = children.firstOrNull { child ->
                OriginalBackupFiles.isPlaylists(child.name)
            } ?: children.firstOrNull { child ->
                val n = child.name.lowercase()
                n.contains("playlist") && (n.endsWith(".info") || n.endsWith(".json"))
            } ?: children.firstOrNull { child ->
                val n = child.name.lowercase().replace("’", "'")
                n.endsWith(".info") || n.startsWith("(don't delete)")
            } ?: error("No se encontró el archivo de playlists (.info) dentro de esta carpeta")

            importBackup(target.uri)
        }.getOrElse { err ->
            val simplified = simplifyError(err)
            _lastErrorLog.value = simplified
            "Error al leer carpeta de Drive: ${err.message ?: "Archivo no encontrado"}"
        }
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
        val lower = name.lowercase().trim().replace("’", "'")
        return when {
            OriginalBackupFiles.isPlaylists(name) -> "Playlists de Lark en Drive (.info)"
            lower.contains("playlists you backed up in lark player") -> "Playlists de Lark en Drive (.info)"
            lower.endsWith(".lrc") -> "Copia local Lark (.lrc)"
            lower.endsWith(".info") && lower.contains("playlist") -> "Playlists de Lark en Drive (.info)"
            lower.endsWith(".info") && lower.contains("play count") -> "Contadores de Lark (.info)"
            lower.endsWith(".info") -> "Respaldo Lark Drive (.info)"
            lower.endsWith(".json") && (lower.contains("playlist") || lower.contains("lark") || lower.contains("backup")) ->
                "Respaldo Drive / JSON (.json)"
            lower.contains("lark-backup") && lower.endsWith(".zip") -> "Copia LarkClean (.zip)"
            lower == "vlc_database" || lower.startsWith("vlc_database") -> "Base de datos Lark"
            lower.endsWith(".zip") && (lower.contains("lark") || lower.contains("backup")) -> "Copia de respaldo (.zip)"
            else -> null
        }
    }
}
