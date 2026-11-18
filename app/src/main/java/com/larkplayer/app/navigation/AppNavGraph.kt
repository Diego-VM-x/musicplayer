package com.larkplayer.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.backup.ui.BackupScreen
import com.larkplayer.feature.equalizer.ui.EqualizerScreen
import com.larkplayer.feature.library.ui.HiddenFilesScreen
import com.larkplayer.feature.library.ui.LibraryScreen
import com.larkplayer.feature.library.ui.PlaylistDetailScreen
import com.larkplayer.feature.lyrics.ui.LyricsScreen
import com.larkplayer.feature.player.ui.MiniPlayer
import com.larkplayer.feature.player.ui.PlayerScreen
import com.larkplayer.feature.player.ui.QueueScreen
import com.larkplayer.feature.player.ui.SleepTimerScreen
import com.larkplayer.feature.settings.ui.AboutScreen
import com.larkplayer.feature.settings.ui.FeedbackScreen
import com.larkplayer.feature.settings.ui.LanguageScreen
import com.larkplayer.feature.settings.ui.NotificationSettingsScreen
import com.larkplayer.feature.settings.ui.PlayTimeScreen
import com.larkplayer.feature.settings.ui.PlaybackSettingsScreen
import com.larkplayer.feature.settings.ui.RecentlyDeletedScreen
import com.larkplayer.feature.settings.ui.SettingsScreen
import com.larkplayer.feature.settings.ui.ThemeScreen
import kotlinx.coroutines.launch

private object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val PLAYLIST = "playlist/{playlistId}"
    fun playlist(id: Long) = "playlist/$id"
    const val SETTINGS = "settings"
    const val LYRICS = "lyrics"
    const val BACKUP = "backup"
    const val QUEUE = "queue"
    const val SLEEP = "sleep"
    const val EQUALIZER = "equalizer"
    const val PLAY_TIME = "play_time"
    const val HIDDEN_FILES = "hidden_files"
    const val RECENTLY_DELETED = "recently_deleted"
    const val PLAYBACK_SETTINGS = "playback_settings"
    const val NOTIFICATIONS = "notifications"
    const val LANGUAGE = "language"
    const val THEME = "theme"
    const val ABOUT = "about"
    const val FEEDBACK = "feedback"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryHome(navController)
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onLyrics = { navController.navigate(Routes.LYRICS) },
                onQueue = { navController.navigate(Routes.QUEUE) },
                onSleepTimer = { navController.navigate(Routes.SLEEP) },
                onEqualizer = { navController.navigate(Routes.EQUALIZER) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
        ) {
            PlaylistDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
            )
        }
        composable(Routes.LYRICS) {
            LyricsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QUEUE) { QueueScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SLEEP) { SleepTimerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.EQUALIZER) { EqualizerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPlayTime = { navController.navigate(Routes.PLAY_TIME) },
                onBackup = { navController.navigate(Routes.BACKUP) },
                onTheme = { navController.navigate(Routes.THEME) },
                onSleepTimer = { navController.navigate(Routes.SLEEP) },
                onHiddenFiles = { navController.navigate(Routes.HIDDEN_FILES) },
                onRecentlyDeleted = { navController.navigate(Routes.RECENTLY_DELETED) },
                onPlaybackSettings = { navController.navigate(Routes.PLAYBACK_SETTINGS) },
                onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLanguage = { navController.navigate(Routes.LANGUAGE) },
                onFeedback = { navController.navigate(Routes.FEEDBACK) },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.PLAY_TIME) { PlayTimeScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.BACKUP) { BackupScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HIDDEN_FILES) { HiddenFilesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.RECENTLY_DELETED) { RecentlyDeletedScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PLAYBACK_SETTINGS) { PlaybackSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS) { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.LANGUAGE) { LanguageScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.THEME) { ThemeScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.FEEDBACK) { FeedbackScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun LibraryHome(navController: NavHostController) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = ApplePalette.Surface) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 28.dp)) {
                    Text("Lark Player", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Biblioteca local", color = ApplePalette.TextSecondary, fontSize = 12.sp)
                }
                DrawerItem("Ajustes", Icons.Rounded.Settings) {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.SETTINGS)
                }
                DrawerItem("Tema", Icons.Rounded.Palette) {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.THEME)
                }
                DrawerItem("Ajustes de reproducción", Icons.Rounded.Headphones) {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.PLAYBACK_SETTINGS)
                }
                DrawerItem("Ecualizador", Icons.Rounded.Equalizer) {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.EQUALIZER)
                }
                DrawerItem("Temporizador para dormir", Icons.Rounded.Timer) {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.SLEEP)
                }
                DrawerItem("Respaldar y guardar", Icons.Rounded.CloudUpload) {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.BACKUP)
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            LibraryScreen(
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
            )
            MiniPlayer(
                onOpen = { navController.navigate(Routes.PLAYER) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = false,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = Color.White,
            unselectedIconColor = Color(0xFFCFCFD5),
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
