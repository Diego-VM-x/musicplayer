package com.larkplayer.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: BackupManager,
) : ViewModel() {
    suspend fun export(uri: Uri): String =
        if (manager.export(uri).isSuccess) "Backup exported successfully" else "Export failed"

    suspend fun import(uri: Uri): String =
        if (manager.import(uri).isSuccess) "Backup restored successfully" else "Import failed"
}
