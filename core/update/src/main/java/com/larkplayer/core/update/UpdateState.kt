package com.larkplayer.core.update

/** Observable states of the in-app update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: AppUpdateInfo) : UpdateState
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Int? = null,
        val waitingWifi: Boolean = false,
    ) : UpdateState
    data class Verifying(val info: AppUpdateInfo) : UpdateState
    data class Ready(val info: AppUpdateInfo, val apkPath: String) : UpdateState
    data class Error(val info: AppUpdateInfo?, val message: String) : UpdateState
}

/** Preferences shown in Ajustes → Actualizaciones. */
data class UpdatePrefs(
    val repo: String = "Diego-VM-x/musicplayer",
    val autoDownload: Boolean = true,
    val wifiOnly: Boolean = true,
)
