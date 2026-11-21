package com.larkplayer.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.larkplayer.core.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class LarkApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        installCrashLogger()
        UpdateManager.initFromPackage(this)
        // Defer the network check until after the first frames so it never
        // competes with drawing the library.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            delay(1_500)
            UpdateManager.checkNow(manual = false)
        }
    }

    /**
     * Writes the last uncaught exception to app-specific external storage so a
     * crash can be diagnosed without a USB/adb connection:
     * `Android/data/<package>/files/logs/crash.txt`.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(getExternalFilesDir(null), "logs").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(dir, "crash.txt").writeText(
                    buildString {
                        appendLine("LarkClean crash — $stamp")
                        appendLine("Thread: ${thread.name}")
                        appendLine(Log.getStackTraceString(throwable))
                        appendLine()
                    }
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
