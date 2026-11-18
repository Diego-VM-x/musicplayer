package com.larkplayer.core.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import java.io.File

/**
 * Opens the system installer, requesting "install unknown apps" first if needed.
 * Android always asks the user for a final confirmation — silent install is not
 * possible for APK-distributed apps.
 */
class UpdateInstallActivity : Activity() {
    private var pendingPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateManager.initFromPackage(this)
        val path = intent?.getStringExtra(AppUpdate.EXTRA_APK_PATH)
        if (!path.isNullOrBlank()) {
            installFromPath(path)
            return
        }
        val url = intent?.getStringExtra(AppUpdate.EXTRA_APK_URL)
        if (!url.isNullOrBlank()) {
            val info = AppUpdateInfo(
                versionCode = intent.getIntExtra(AppUpdate.EXTRA_VERSION_CODE, 0),
                versionName = intent.getStringExtra(AppUpdate.EXTRA_VERSION_NAME).orEmpty(),
                apkUrl = url,
                sha256 = intent.getStringExtra(AppUpdate.EXTRA_APK_SHA256).orEmpty(),
                sizeBytes = intent.getLongExtra(AppUpdate.EXTRA_APK_SIZE, 0L),
            )
            UpdateManager.downloadNow(info, allowMetered = false)
            finish()
            return
        }
        finish()
    }

    private fun installFromPath(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "El APK ya no está disponible.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingPath = path
            Toast.makeText(this, "Permite instalar apps desconocidas para continuar.", Toast.LENGTH_LONG).show()
            val opened = runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }.isSuccess
            if (!opened) {
                pendingPath = null
                finish()
            }
            return
        }
        pendingPath = null
        if (!AppUpdate.installApk(this, file)) {
            Toast.makeText(this, "No se pudo abrir el instalador.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        pendingPath?.let { path ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                installFromPath(path)
            }
        }
    }
}
