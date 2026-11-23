package com.larkplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.app.navigation.AppNavGraph
import com.larkplayer.core.common.MediaScanBus
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.designsystem.component.AppBackdrop
import com.larkplayer.core.designsystem.component.AppBackground
import com.larkplayer.core.designsystem.component.LocalAppBackground
import com.larkplayer.core.designsystem.theme.LarkTheme
import com.larkplayer.core.model.ThemeConfig
import com.larkplayer.core.update.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var scanBus: MediaScanBus

    /** Tapping the media notification asks the graph to open the full player. */
    private val openPlayerRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var openPlayerOnStart = false

    companion object {
        const val EXTRA_OPEN_PLAYER = "lark_open_player"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { scanBus.requestScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPlayerOnStart = intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true

        // Draw behind the status bar but keep the UI below it: the gradient shows
        // through the transparent bar without shifting the whole interface up.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        // Run permission check and battery optimization prompt after initial UI frame setup
        // so the app window paints immediately without waiting.
        // Nothing that can show a dialog or steal focus runs during the first
        // frames. The library is already painted from the Room cache.
        window.decorView.postDelayed({ requestMediaPermissions() }, 1_200L)
        window.decorView.postDelayed({ requestBatteryOptimizationExemption() }, 4_000L)

        setContent {
            val config by settingsDataStore.themeConfig.collectAsStateWithLifecycle(ThemeConfig())
            val bgUri by settingsDataStore.appBackgroundUri.collectAsStateWithLifecycle("")
            val bgVariant by settingsDataStore.backgroundVariant.collectAsStateWithLifecycle(0)
            LarkTheme(config = config) {
                CompositionLocalProvider(
                    LocalAppBackground provides AppBackground(
                        imageUri = bgUri.ifBlank { null },
                        variantIndex = bgVariant,
                    ),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppBackdrop(Modifier.fillMaxSize())
                        Box(
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                        ) {
                            AppNavGraph(
                                openPlayerOnStart = openPlayerOnStart,
                                openPlayerRequests = openPlayerRequests,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            openPlayerRequests.tryEmit(Unit)
        }
    }

    /** One-time system request so playback survives closing the app / Doze. */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val flags = getSharedPreferences("lark_runtime", MODE_PRIVATE)
        if (flags.getBoolean("battery_prompt_done", false)) return
        val power = getSystemService(PowerManager::class.java)
        if (power?.isIgnoringBatteryOptimizations(packageName) == true) {
            flags.edit().putBoolean("battery_prompt_done", true).apply()
            return
        }
        flags.edit().putBoolean("battery_prompt_done", true).apply()
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun requestMediaPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 34) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        else scanBus.requestScan()
    }

    override fun onResume() {
        super.onResume()
        scanBus.requestScan()
        UpdateManager.checkNow(manual = false)
    }
}
