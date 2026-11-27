package com.larkplayer.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Result of validating a downloaded APK; REJECTED must not be retried forever. */
enum class VerifyOutcome { SUCCESS, RETRY, REJECTED }

/**
 * Single source of truth for the in-app updater: checks GitHub releases, asks
 * before downloading, verifies the APK and exposes the state to the settings UI.
 */
object UpdateManager {
    private const val PREFS_NAME = "lark_update"
    private const val MIN_CHECK_INTERVAL_MS = 5 * 60_000L
    private const val CH_AVAILABLE = "lark_update_available"
    private const val CH_READY = "lark_update_ready"
    private const val CH_ERROR = "lark_update_error"

    private var context: Context? = null
    private var initialized = false
    private var currentCode = 0
    private var currentName = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val _prefsState = MutableStateFlow(UpdatePrefs())
    val prefsState: StateFlow<UpdatePrefs> = _prefsState

    val versionName: String get() = currentName
    val versionCode: Int get() = currentCode

    private val preferences
        get() = context!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isInitialized(): Boolean = initialized

    /** Reads the installed version from PackageManager (no BuildConfig needed). */
    fun initFromPackage(appContext: Context) {
        val info = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull() ?: return
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") info.versionCode
        }
        init(appContext, code, info.versionName.orEmpty())
    }

    fun init(appContext: Context, versionCode: Int, versionName: String) {
        if (initialized && this.currentCode == versionCode) return
        context = appContext.applicationContext
        currentCode = versionCode
        currentName = versionName
        initialized = true
        refreshPrefs()
        restoreState()
        UpdateScheduler.schedule(context!!)
    }

    private fun refreshPrefs() {
        val p = preferences
        _prefsState.value = UpdatePrefs(
            autoDownload = p.getBoolean(KEY_AUTO, true),
            wifiOnly = p.getBoolean(KEY_WIFI, true),
        )
    }

    val repository: String get() = AppUpdate.GITHUB_REPO

    fun setAutoDownload(enabled: Boolean) {
        checkInitialized()
        preferences.edit().putBoolean(KEY_AUTO, enabled).apply()
        refreshPrefs()
    }

    fun setWifiOnly(enabled: Boolean) {
        checkInitialized()
        preferences.edit().putBoolean(KEY_WIFI, enabled).apply()
        refreshPrefs()
    }

    fun checkNow(manual: Boolean) {
        scope.launch { checkNowSuspend(manual) }
    }

    /**
     * Same check as [checkNow] but suspends until the network result is known.
     * WorkManager calls this so a failed fetch can be retried instead of being
     * reported as a successful no-op.
     */
    suspend fun checkNowSuspend(manual: Boolean): Boolean {
        if (!initialized) return false
        val now = System.currentTimeMillis()
        if (!manual && now - preferences.getLong(KEY_LAST_CHECK, 0L) < MIN_CHECK_INTERVAL_MS) return true
        // A background check (onResume / worker) must not hide the "Install now"
        // button of an APK that is already verified and ready.
        val readyInfo = (_state.value as? UpdateState.Ready)?.info
        preferences.edit().putLong(KEY_LAST_CHECK, now).apply()
        if (readyInfo == null) _state.value = UpdateState.Checking
        return when (val result = AppUpdate.fetchLatest()) {
            is UpdateFetchResult.Found -> {
                if (readyInfo == null || result.info.versionCode > readyInfo.versionCode) {
                    onUpdateFound(result.info)
                }
                true
            }
            UpdateFetchResult.NotFound -> {
                if (readyInfo == null) _state.value = UpdateState.Idle
                true
            }
            is UpdateFetchResult.Error -> {
                if (readyInfo == null) _state.value = UpdateState.Error(null, result.message)
                // The worker still retries on a network error, but the UI keeps
                // the ready APK available.
                false
            }
        }
    }

    private fun onUpdateFound(info: AppUpdateInfo) {
        if (!AppUpdate.isNewer(info, currentCode, currentName)) {
            _state.value = UpdateState.Idle
            return
        }
        _state.value = UpdateState.Available(info)
        val alreadyNotified = preferences.getString(KEY_NOTIFIED_VERSION, "") == info.versionName
        if (!alreadyNotified) {
            preferences.edit().putString(KEY_NOTIFIED_VERSION, info.versionName).apply()
            notifyAvailable(context!!, info)
        }
        if (_prefsState.value.autoDownload) {
            downloadNow(info, allowMetered = false)
        }
    }

    fun downloadNow(info: AppUpdateInfo, allowMetered: Boolean) {
        checkInitialized()
        if (!AppUpdate.isSecureUrl(info.apkUrl)) {
            _state.value = UpdateState.Error(info, "La URL del APK no es segura (HTTPS).")
            return
        }
        val wifiOnly = _prefsState.value.wifiOnly && !allowMetered
        if (wifiOnly && !AppUpdate.isUnmetered(context!!)) {
            _state.value = UpdateState.Downloading(info, waitingWifi = true)
            return
        }
        val id = AppUpdate.startDownload(context!!, info, wifiOnly)
        if (id <= 0L) {
            _state.value = UpdateState.Error(info, "No se pudo iniciar la descarga.")
            return
        }
        preferences.edit().apply {
            putLong(KEY_DOWNLOAD_ID, id)
            putString(KEY_PENDING_VERSION, info.versionName)
            putInt(KEY_PENDING_CODE, info.versionCode)
            putString(KEY_PENDING_SHA, info.sha256)
            putLong(KEY_PENDING_SIZE, info.sizeBytes)
            putString(KEY_PENDING_URL, info.apkUrl)
        }.apply()
        cancelNotifications(context!!)
        _state.value = UpdateState.Downloading(info, progress = 0)
        pollDownload(id, info)
    }

    private fun pollDownload(id: Long, info: AppUpdateInfo) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var polls = 0
            var terminal = false
            while (isActive && polls < MAX_DOWNLOAD_POLLS) {
                polls++
                val snapshot = AppUpdate.queryDownload(context!!, id)
                when (snapshot?.status) {
                    android.app.DownloadManager.STATUS_RUNNING,
                    android.app.DownloadManager.STATUS_PENDING,
                    android.app.DownloadManager.STATUS_PAUSED, -> {
                        val progress = if (snapshot.totalBytes > 0) {
                            (snapshot.downloadedBytes * 100 / snapshot.totalBytes).toInt()
                        } else null
                        _state.value = UpdateState.Downloading(info, progress = progress)
                    }
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        terminal = true
                        onDownloadComplete(id)
                        break
                    }
                    android.app.DownloadManager.STATUS_FAILED -> {
                        terminal = true
                        onDownloadFailed(id, "La descarga falló. Puedes reintentarla desde Ajustes.")
                        break
                    }
                    // The DownloadManager row can disappear ( reboot / user
                    // clears Downloads ). Without an else branch the coroutine
                    // polled forever and the UI stayed on "Descargando".
                    else -> {
                        terminal = true
                        onDownloadFailed(id, "No se pudo comprobar la descarga. Puedes reintentarla.")
                        break
                    }
                }
                delay(1_000)
            }
            // A successful download on the last poll is terminal: without this
            // flag the timeout branch overwrote Ready with an Error.
            if (!terminal && isActive) {
                onDownloadFailed(id, "La descarga tardó demasiado. Puedes reintentarla.")
            }
        }
    }

    fun onDownloadComplete(id: Long) {
        if (!initialized || id != preferences.getLong(KEY_DOWNLOAD_ID, -1L)) return
        val info = pendingInfo() ?: return
        cancelNotifications(context!!)
        _state.value = UpdateState.Verifying(info)
        UpdateScheduler.verify(context!!, id)
    }

    fun verifyDownload(id: Long): VerifyOutcome {
        if (!initialized) return VerifyOutcome.RETRY
        val p = preferences
        if (id != p.getLong(KEY_DOWNLOAD_ID, -1L)) return VerifyOutcome.REJECTED
        val info = pendingInfo() ?: return VerifyOutcome.REJECTED
        val snapshot = AppUpdate.queryDownload(context!!, id)
        if (snapshot?.status != android.app.DownloadManager.STATUS_SUCCESSFUL) {
            return VerifyOutcome.RETRY
        }
        val file = snapshot.localUri?.let { runCatching { File(it.toUri().path ?: "").takeIf { f -> f.exists() } }.getOrNull() }
            ?: AppUpdate.targetFile(context!!, info)
        val error = AppUpdate.validateApk(context!!, file, info, currentCode)
        if (error != null) {
            runCatching { file.delete() }
            clearPending()
            _state.value = UpdateState.Error(info, error)
            notifyError(context!!, error)
            // Permanent rejection: the error was already shown and the pending
            // id cleared, so WorkManager must not retry forever.
            return VerifyOutcome.REJECTED
        }
        p.edit().apply {
            putInt(KEY_READY_CODE, info.versionCode)
            putString(KEY_READY_VERSION, info.versionName)
            putString(KEY_READY_PATH, file.absolutePath)
            putString(KEY_READY_URL, info.apkUrl)
            putString(KEY_READY_SHA, info.sha256)
            putLong(KEY_READY_SIZE, info.sizeBytes)
        }.apply()
        clearPending()
        deleteOldApks(file)
        _state.value = UpdateState.Ready(info, file.absolutePath)
        notifyReady(context!!, info, file)
        return VerifyOutcome.SUCCESS
    }

    fun onDownloadFailed(id: Long, message: String) {
        if (id != preferences.getLong(KEY_DOWNLOAD_ID, -1L)) return
        val info = pendingInfo()
        clearPending()
        _state.value = UpdateState.Error(info, message)
        notifyError(context!!, message)
    }

    fun cancelDownload() {
        if (!initialized) return
        pollJob?.cancel()
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        AppUpdate.removeDownload(context!!, id)
        clearPending()
        cancelNotifications(context!!)
        _state.value = UpdateState.Idle
    }

    fun installReady() {
        if (!initialized) return
        val path = preferences.getString(KEY_READY_PATH, "").orEmpty()
        val error = validateInstallPath(path)
        if (error != null) {
            clearReady()
            _state.value = UpdateState.Error(null, error)
            return
        }
        val intent = Intent(context!!, UpdateInstallActivity::class.java).apply {
            putExtra(AppUpdate.EXTRA_APK_PATH, path)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context!!.startActivity(intent) }
    }

    /**
     * The APK is re-validated immediately before the installer is opened. A
     * file can be replaced or corrupted between verification and installation
     * (including while the app was in the background), and `Ready` state is
     * restored from preferences after a process death.
     */
    fun validateInstallPath(path: String): String? {
        if (!initialized) return "El actualizador no está listo."
        if (path.isBlank()) return "El APK ya no está disponible."
        val file = File(path)
        if (!file.exists()) return "El APK ya no está disponible."
        val info = readyInfo() ?: pendingInfo()
            ?: return "No hay datos de la actualización; vuelve a comprobarla."
        return AppUpdate.validateApk(context!!, file, info, currentCode)
    }

    private fun readyInfo(): AppUpdateInfo? {
        val p = preferences
        val code = p.getInt(KEY_READY_CODE, 0)
        if (code <= 0) return null
        return AppUpdateInfo(
            versionCode = code,
            versionName = p.getString(KEY_READY_VERSION, code.toString()).orEmpty(),
            apkUrl = p.getString(KEY_READY_URL, "").orEmpty(),
            sha256 = p.getString(KEY_READY_SHA, "").orEmpty(),
            sizeBytes = p.getLong(KEY_READY_SIZE, 0L),
        )
    }

    private fun clearReady() {
        preferences.edit().apply {
            remove(KEY_READY_CODE)
            remove(KEY_READY_VERSION)
            remove(KEY_READY_PATH)
            remove(KEY_READY_URL)
            remove(KEY_READY_SHA)
            remove(KEY_READY_SIZE)
        }.apply()
    }

    fun onCheckError(message: String) {
        if (initialized) _state.value = UpdateState.Error(null, message)
    }

    fun onUpToDate() {
        if (initialized) _state.value = UpdateState.Idle
    }

    private fun pendingInfo(): AppUpdateInfo? {
        val p = preferences
        val code = p.getInt(KEY_PENDING_CODE, 0)
        if (code <= 0) return null
        return AppUpdateInfo(
            versionCode = code,
            versionName = p.getString(KEY_PENDING_VERSION, code.toString()).orEmpty(),
            apkUrl = p.getString(KEY_PENDING_URL, "").orEmpty(),
            sha256 = p.getString(KEY_PENDING_SHA, "").orEmpty(),
            sizeBytes = p.getLong(KEY_PENDING_SIZE, 0L),
        )
    }

    private fun clearPending() {
        preferences.edit().apply {
            remove(KEY_DOWNLOAD_ID)
            remove(KEY_PENDING_VERSION)
            remove(KEY_PENDING_CODE)
            remove(KEY_PENDING_SHA)
            remove(KEY_PENDING_SIZE)
            remove(KEY_PENDING_URL)
        }.apply()
    }

    private fun restoreState() {
        val p = preferences
        val readyCode = p.getInt(KEY_READY_CODE, 0)
        val readyPath = p.getString(KEY_READY_PATH, "").orEmpty()
        if (readyCode > currentCode && readyPath.isNotBlank() && File(readyPath).exists()) {
            val info = AppUpdateInfo(
                versionCode = readyCode,
                versionName = p.getString(KEY_READY_VERSION, readyCode.toString()).orEmpty(),
                apkUrl = p.getString(KEY_READY_URL, "").orEmpty(),
                sha256 = p.getString(KEY_READY_SHA, "").orEmpty(),
                sizeBytes = p.getLong(KEY_READY_SIZE, 0L),
            )
            if (AppUpdate.validateApk(context!!, File(readyPath), info, currentCode) == null) {
                _state.value = UpdateState.Ready(info, readyPath)
                return
            }
            clearReady()
        }
        val id = p.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0) {
            when (AppUpdate.queryDownload(context!!, id)?.status) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> onDownloadComplete(id)
                android.app.DownloadManager.STATUS_FAILED -> onDownloadFailed(
                    id,
                    "La descarga falló. Puedes reintentarla desde Ajustes.",
                )
                else -> pendingInfo()?.let { pollDownload(id, it) }
            }
        }
    }

    private fun deleteOldApks(current: File) {
        val dirs = listOfNotNull(
            context!!.getExternalFilesDir("updates"),
            File(context!!.filesDir, "updates"),
        )
        dirs.forEach { dir ->
            dir.listFiles()?.filter { it.name.endsWith(".apk", true) && it.absolutePath != current.absolutePath }
                ?.forEach { runCatching { it.delete() } }
        }
    }

    private fun checkInitialized() = check(initialized) { "UpdateManager.init() must be called first" }

    // ---- Notifications -----------------------------------------------------

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(CH_AVAILABLE, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_READY, "Actualización lista", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_ERROR, "Error de actualización", NotificationManager.IMPORTANCE_DEFAULT),
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    private fun installIntent(context: Context, info: AppUpdateInfo, path: String?): PendingIntent {
        val intent = Intent(context, UpdateInstallActivity::class.java).apply {
            putExtra(AppUpdate.EXTRA_VERSION_NAME, info.versionName)
            putExtra(AppUpdate.EXTRA_VERSION_CODE, info.versionCode)
            putExtra(AppUpdate.EXTRA_APK_SHA256, info.sha256)
            putExtra(AppUpdate.EXTRA_APK_SIZE, info.sizeBytes)
            if (path != null) putExtra(AppUpdate.EXTRA_APK_PATH, path)
            else putExtra(AppUpdate.EXTRA_APK_URL, info.apkUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            7701,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifyAvailable(context: Context, info: AppUpdateInfo) {
        ensureChannels(context)
        val pending = installIntent(context, info, null)
        val text = info.notes.ifBlank { "Lark Player ${info.versionName} está disponible. Toca para descargar." }
        val notification = NotificationCompat.Builder(context, CH_AVAILABLE)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Nueva actualización disponible")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .addAction(android.R.drawable.stat_sys_download, "Actualizar", pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7701, notification) }
    }

    fun notifyReady(context: Context, info: AppUpdateInfo, file: File) {
        ensureChannels(context)
        val pending = installIntent(context, info, file.absolutePath)
        val notification = NotificationCompat.Builder(context, CH_READY)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Actualización lista para instalar")
            .setContentText("Lark Player ${info.versionName} se descargó correctamente.")
            .setContentIntent(pending)
            .addAction(android.R.drawable.stat_sys_download_done, "Instalar", pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).apply {
                cancel(7701)
                notify(7702, notification)
            }
        }
    }

    fun notifyError(context: Context, message: String) {
        ensureChannels(context)
        val pending = PendingIntent.getActivity(
            context,
            7703,
            Intent(context, UpdateInstallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CH_ERROR)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Error al actualizar")
            .setContentText(message)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7703, notification) }
    }

    fun cancelNotifications(context: Context) = runCatching {
        NotificationManagerCompat.from(context).apply {
            cancel(7701)
            cancel(7702)
            cancel(7703)
        }
    }

    /** 1 s per poll, 30 min ceiling: a lost DownloadManager row cannot spin forever. */
    private const val MAX_DOWNLOAD_POLLS = 1_800

    private const val KEY_AUTO = "auto_download"
    private const val KEY_WIFI = "wifi_only"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val KEY_NOTIFIED_VERSION = "notified_version"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_PENDING_CODE = "pending_code"
    private const val KEY_PENDING_SHA = "pending_sha"
    private const val KEY_PENDING_SIZE = "pending_size"
    private const val KEY_PENDING_URL = "pending_url"
    private const val KEY_READY_CODE = "ready_code"
    private const val KEY_READY_VERSION = "ready_version"
    private const val KEY_READY_PATH = "ready_path"
    private const val KEY_READY_URL = "ready_url"
    private const val KEY_READY_SHA = "ready_sha"
    private const val KEY_READY_SIZE = "ready_size"
}
