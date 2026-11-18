package com.larkplayer.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.larkplayer.core.designsystem.component.GlassNavigationBar
import com.larkplayer.core.designsystem.component.glassNavItemColors
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.backup.ui.BackupScreen
import com.larkplayer.feature.equalizer.ui.EqualizerScreen
import com.larkplayer.feature.library.ui.LibraryScreen
import com.larkplayer.feature.lyrics.ui.LyricsScreen
import com.larkplayer.feature.player.ui.MiniPlayer
import com.larkplayer.feature.player.ui.PlayerScreen
import com.larkplayer.feature.player.ui.QueueScreen
import com.larkplayer.feature.player.ui.SleepTimerScreen
import com.larkplayer.feature.settings.ui.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val LYRICS = "lyrics"
    const val BACKUP = "backup"
    const val QUEUE = "queue"
    const val SLEEP = "sleep"
    const val EQUALIZER = "equalizer"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val navColors = glassNavItemColors(accent, ApplePalette.TextSecondary)

    val items = listOf(
        Triple(Routes.LIBRARY, "Library", Icons.Default.LibraryMusic),
        Triple(Routes.PLAYER, "Player", Icons.Default.PlayCircle),
        Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    )

    Scaffold(
        containerColor = ApplePalette.Background,
        bottomBar = {
            androidx.compose.material3.Surface(color = Color(0x14000000)) {
            androidx.compose.material3.NavigationBar(
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ) {
                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == route } == true,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = navColors,
                    )
                }
            }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LIBRARY) {
                Column {
                    LibraryScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
                    MiniPlayer(onOpen = { navController.navigate(Routes.PLAYER) })
                }
            }
            composable(Routes.PLAYER) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onLyrics = { navController.navigate(Routes.LYRICS) },
                    onQueue = { navController.navigate(Routes.QUEUE) },
                    onSleepTimer = { navController.navigate(Routes.SLEEP) },
                    onEqualizer = { navController.navigate(Routes.EQUALIZER) },
                )
            }
            composable(Routes.LYRICS) { LyricsScreen() }
            composable(Routes.QUEUE) { QueueScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SLEEP) { SleepTimerScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.EQUALIZER) { EqualizerScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.BACKUP) { BackupScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
