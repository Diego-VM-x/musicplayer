package com.larkplayer.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.larkplayer.core.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class LarkApp : Application() {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Album art is cached on disk and in memory. The singleton is created lazily
     * on the first image request (after the first frame), so nothing image
     * related runs during app startup.
     */
    private fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("lark_image_cache"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Only stores a factory; the ImageLoader itself is built on first use.
        // (Implementing ImageLoaderFactory breaks Hilt's generated Application.)
        Coil.setImageLoader { newImageLoader() }

        // Nothing here touches disk, network or the worker database. The first
        // Compose frame is the only thing that runs on the main thread.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            installCrashLogger()
            // WorkManager's manifest initializer was removed: start it after the
            // UI is already visible so its DB/openHelper work is not on the
            // cold-start path.
            delay(1_500)
            withContext(Dispatchers.Main) {
                runCatching {
                    if (runCatching { WorkManager.getInstance(this@LarkApp) }.isFailure) {
                        WorkManager.initialize(
                            this@LarkApp,
                            Configuration.Builder().setWorkerFactory(workerFactory).build(),
                        )
                    }
                }
            }
            UpdateManager.initFromPackage(this@LarkApp)
            delay(20_000)
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
