package com.larkplayer.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.larkplayer.feature.backup.ui.BackupScreen
import com.larkplayer.feature.library.ui.LibraryScreen
import com.larkplayer.feature.lyrics.ui.LyricsScreen
import com.larkplayer.feature.player.ui.MiniPlayer
import com.larkplayer.feature.player.ui.PlayerScreen
import com.larkplayer.feature.settings.ui.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val LYRICS = "lyrics"
    const val BACKUP = "backup"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    val items = listOf(
        Triple(Routes.LIBRARY, "Library", Icons.Default.LibraryMusic),
        Triple(Routes.PLAYER, "Player", Icons.Default.PlayCircle),
        Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == route } == true,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
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
                androidx.compose.foundation.layout.Column {
                    LibraryScreen(
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                    MiniPlayer(onOpen = { navController.navigate(Routes.PLAYER) })
                }
            }
            composable(Routes.PLAYER) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onLyrics = { navController.navigate(Routes.LYRICS) },
                )
            }
            composable(Routes.LYRICS) {
                LyricsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
