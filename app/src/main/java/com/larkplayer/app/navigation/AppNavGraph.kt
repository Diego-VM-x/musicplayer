package com.larkplayer.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.larkplayer.core.designsystem.component.LocalFlatSurface
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.backup.ui.BackupScreen
import com.larkplayer.feature.equalizer.ui.EqualizerScreen
import com.larkplayer.feature.library.ui.HiddenFilesScreen
import com.larkplayer.feature.library.ui.LibraryScreen
import com.larkplayer.feature.library.ui.MemoriesScreen
import com.larkplayer.feature.library.ui.PlaylistDetailScreen
import com.larkplayer.feature.lyrics.ui.LyricsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.feature.player.ui.MiniPlayer
import com.larkplayer.feature.player.ui.MiniPlayerOverlay
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
import com.larkplayer.feature.settings.ui.UpdateSettingsScreen
import kotlinx.coroutines.delay
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
    const val UPDATES = "updates"
    const val MEMORIES = "memories"
}

private val MINI_PLAYER_HIDDEN_ROUTES = setOf(
    Routes.PLAYER,
    Routes.PLAYLIST,
    Routes.SETTINGS,
    Routes.PLAY_TIME,
    Routes.BACKUP,
    Routes.HIDDEN_FILES,
    Routes.RECENTLY_DELETED,
    Routes.PLAYBACK_SETTINGS,
    Routes.NOTIFICATIONS,
    Routes.LANGUAGE,
    Routes.THEME,
    Routes.ABOUT,
    Routes.FEEDBACK,
    Routes.UPDATES,
    Routes.MEMORIES,
)

/** Routes that use the flat (non-glass) design language. */
private val FLAT_ROUTES = setOf(
    Routes.PLAYLIST,
    Routes.SETTINGS,
    Routes.PLAY_TIME,
    Routes.BACKUP,
    Routes.HIDDEN_FILES,
    Routes.RECENTLY_DELETED,
    Routes.PLAYBACK_SETTINGS,
    Routes.NOTIFICATIONS,
    Routes.LANGUAGE,
    Routes.THEME,
    Routes.ABOUT,
    Routes.FEEDBACK,
    Routes.UPDATES,
    Routes.MEMORIES,
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Delay the floating mini-player (and its PlayerViewModel/Hilt graph) until
    // after the first composed frame. Nothing playback-related runs at launch.
    var miniReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_000)
        miniReady = true
    }
    val miniAllowed = miniReady &&
        MiniPlayerOverlay.visible.collectAsStateWithLifecycle().value &&
        currentRoute !in MINI_PLAYER_HIDDEN_ROUTES

    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalFlatSurface provides (currentRoute in FLAT_ROUTES)) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        enterTransition = { slideInHorizontally { it / 5 } + fadeIn() },
        exitTransition = { slideOutHorizontally { -it / 6 } + fadeOut() },
        popEnterTransition = { slideInHorizontally { -it / 5 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 5 } + fadeOut() },
    ) {
        composable(Routes.LIBRARY) {
            LibraryHome(navController)
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onLyrics = { navController.go(Routes.LYRICS) },
                onQueue = { navController.go(Routes.QUEUE) },
                onSleepTimer = { navController.go(Routes.SLEEP) },
                onEqualizer = { navController.go(Routes.EQUALIZER) },
                onSettings = { navController.go(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
        ) {
            PlaylistDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.go(Routes.PLAYER) },
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
                onPlayTime = { navController.go(Routes.PLAY_TIME) },
                onBackup = { navController.go(Routes.BACKUP) },
                onTheme = { navController.go(Routes.THEME) },
                onSleepTimer = { navController.go(Routes.SLEEP) },
                onHiddenFiles = { navController.go(Routes.HIDDEN_FILES) },
                onRecentlyDeleted = { navController.go(Routes.RECENTLY_DELETED) },
                onPlaybackSettings = { navController.go(Routes.PLAYBACK_SETTINGS) },
                onNotifications = { navController.go(Routes.NOTIFICATIONS) },
                onLanguage = { navController.go(Routes.LANGUAGE) },
                onFeedback = { navController.go(Routes.FEEDBACK) },
                onAbout = { navController.go(Routes.ABOUT) },
                onUpdates = { navController.go(Routes.UPDATES) },
                onMemories = { navController.go(Routes.MEMORIES) },
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
        composable(Routes.UPDATES) { UpdateSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.MEMORIES) {
            MemoriesScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.go(Routes.PLAYER) },
            )
        }
    }
    }

        // Floating mini-player on every screen except full player, settings and
        // the playlist editor (which hides it through MiniPlayerOverlay).
        if (miniAllowed) {
            MiniPlayer(
                onOpen = { navController.go(Routes.PLAYER) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
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
                    navController.go(Routes.SETTINGS)
                }
                DrawerItem("Tema", Icons.Rounded.Palette) {
                    scope.launch { drawerState.close() }
                    navController.go(Routes.THEME)
                }
                DrawerItem("Ajustes de reproducción", Icons.Rounded.Headphones) {
                    scope.launch { drawerState.close() }
                    navController.go(Routes.PLAYBACK_SETTINGS)
                }
                DrawerItem("Ecualizador", Icons.Rounded.Equalizer) {
                    scope.launch { drawerState.close() }
                    navController.go(Routes.EQUALIZER)
                }
                DrawerItem("Temporizador para dormir", Icons.Rounded.Timer) {
                    scope.launch { drawerState.close() }
                    navController.go(Routes.SLEEP)
                }
                DrawerItem("Respaldar y guardar", Icons.Rounded.CloudUpload) {
                    scope.launch { drawerState.close() }
                    navController.go(Routes.BACKUP)
                }
                DrawerItem("Actualizaciones", Icons.Rounded.SystemUpdate) {
                    scope.launch { drawerState.close() }
                    navController.go(Routes.UPDATES)
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            LibraryScreen(
                onOpenPlayer = { navController.go(Routes.PLAYER) },
                onOpenSettings = { navController.go(Routes.SETTINGS) },
                onOpenPlaylist = { id -> navController.go(Routes.playlist(id)) },
            )
        }
    }
}

/** Reuses existing destinations instead of stacking duplicates on every tap. */
private fun NavHostController.go(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
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
