package com.larkplayer.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.larkplayer.core.model.ColorItem
import com.larkplayer.core.model.ThemeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val THEME_SIZE = intPreferencesKey("theme_size")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val GAPLESS = booleanPreferencesKey("gapless")
        val PLAY_WITH_OTHERS = booleanPreferencesKey("play_with_others")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val SLEEP_TIMER_MIN = intPreferencesKey("sleep_timer_min")
        val COLOR_OVERRIDES = stringPreferencesKey("color_overrides") // "mode|attr|value;..."
        val LANGUAGE = stringPreferencesKey("language")
        val FAVORITES = stringPreferencesKey("favorites") // comma separated song ids
        val HIDDEN = stringPreferencesKey("hidden") // comma separated song ids
        val TOTAL_PLAY_MS = longPreferencesKey("total_play_ms")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        val NOTIF_PLAYBACK = booleanPreferencesKey("notification_playback")
        val NOTIF_LOCKSCREEN = booleanPreferencesKey("notification_lockscreen")
        val VISUALIZER_ENABLED = booleanPreferencesKey("visualizer_enabled")
        val VISUALIZER_STYLE = stringPreferencesKey("visualizer_style") // "armonia", "rebote", "ascenso"
        val APP_BACKGROUND_URI = stringPreferencesKey("app_background_uri")
        val BACKGROUND_VARIANT = intPreferencesKey("background_variant")
        // Orders and other UI state that must survive a full app restart.
        val LIBRARY_SORT_MODE = intPreferencesKey("library_sort_mode")
        val LIBRARY_TAB = intPreferencesKey("library_tab")
        val PLAYLIST_SORT_MODE = intPreferencesKey("playlist_sort_mode")
        val SHUFFLE_ORDER = stringPreferencesKey("shuffle_order")
    }

    /** One sort mode per playlist detail screen (0 = recientes, 3 = manual). */
    private fun playlistSortKey(playlistId: Long) =
        intPreferencesKey("playlist_detail_sort_$playlistId")

    val themeConfig: Flow<ThemeConfig> = context.settingsDataStore.data.map { p ->
        ThemeConfig(
            useDynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            themeMode = p[Keys.THEME_MODE] ?: 1,
            overrides = (p[Keys.COLOR_OVERRIDES] ?: "").parseColors(),
            accentColor = p[Keys.ACCENT_COLOR] ?: 0xFF0A84FF,
            themeSize = p[Keys.THEME_SIZE] ?: 1,
        )
    }

    val shuffle: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.SHUFFLE] ?: false }
    // REPEAT_MODE_ALL: finishing a queue starts it again instead of stopping.
    val repeatMode: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.REPEAT_MODE] ?: 1 }
    val playbackSpeed: Flow<Float> = context.settingsDataStore.data.map { p -> p[Keys.PLAYBACK_SPEED] ?: 1f }
    val gapless: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.GAPLESS] ?: true }
    val playWithOthers: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.PLAY_WITH_OTHERS] ?: true }
    val backgroundPlayback: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.BACKGROUND_PLAYBACK] ?: true }
    val crossfadeEnabled: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.CROSSFADE_ENABLED] ?: false }
    val crossfadeSeconds: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.CROSSFADE_SECONDS] ?: 4 }
    val sleepTimerMinutes: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.SLEEP_TIMER_MIN] ?: 0 }
    val language: Flow<String> = context.settingsDataStore.data.map { p -> p[Keys.LANGUAGE] ?: "es" }
    val favorites: Flow<Set<Long>> = context.settingsDataStore.data.map { p -> p[Keys.FAVORITES].toIdSet() }
    val hiddenSongIds: Flow<Set<Long>> = context.settingsDataStore.data.map { p -> p[Keys.HIDDEN].toIdSet() }
    val totalPlayMs: Flow<Long> = context.settingsDataStore.data.map { p -> p[Keys.TOTAL_PLAY_MS] ?: 0L }
    val lastScanAt: Flow<Long> = context.settingsDataStore.data.map { p -> p[Keys.LAST_SCAN_AT] ?: 0L }
    val notificationPlayback: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.NOTIF_PLAYBACK] ?: true }
    val notificationLockScreen: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.NOTIF_LOCKSCREEN] ?: true }
    val visualizerEnabled: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.VISUALIZER_ENABLED] ?: false }
    val visualizerStyle: Flow<String> = context.settingsDataStore.data.map { p -> p[Keys.VISUALIZER_STYLE] ?: "armonia" }
    val appBackgroundUri: Flow<String> = context.settingsDataStore.data.map { p -> p[Keys.APP_BACKGROUND_URI].orEmpty() }
    val backgroundVariant: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.BACKGROUND_VARIANT] ?: 0 }

    // ---- Persisted UI order/state -------------------------------------------
    /** General songs list: 0 = recientes, 1 = título, 2 = artista. */
    val librarySortMode: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.LIBRARY_SORT_MODE] ?: 0 }
    /** Last selected library tab (1 = Canciones by default). */
    val libraryTab: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.LIBRARY_TAB] ?: 1 }
    /** Playlists tab: 0 = manual (Room position), 1 = nombre, 2 = creación. */
    val playlistSortMode: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.PLAYLIST_SORT_MODE] ?: 0 }
    /** Shuffled playback order as a comma-separated list of media ids. */
    val shuffleOrder: Flow<String> = context.settingsDataStore.data.map { p -> p[Keys.SHUFFLE_ORDER].orEmpty() }

    fun playlistDetailSortMode(playlistId: Long): Flow<Int> =
        context.settingsDataStore.data.map { p -> p[playlistSortKey(playlistId)] ?: 0 }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setVisualizerEnabled(enabled: Boolean) = edit { it[Keys.VISUALIZER_ENABLED] = enabled }
    suspend fun setVisualizerStyle(style: String) = edit { it[Keys.VISUALIZER_STYLE] = style }
    suspend fun setAppBackgroundUri(uri: String) = edit { it[Keys.APP_BACKGROUND_URI] = uri }
    suspend fun setBackgroundVariant(index: Int) = edit { it[Keys.BACKGROUND_VARIANT] = index }
    suspend fun setThemeMode(mode: Int) = edit { it[Keys.THEME_MODE] = mode }
    suspend fun setThemeSize(size: Int) = edit { it[Keys.THEME_SIZE] = size }
    suspend fun setAccentColor(argb: Long) = edit { it[Keys.ACCENT_COLOR] = argb }
    suspend fun setShuffle(enabled: Boolean) = edit { it[Keys.SHUFFLE] = enabled }
    suspend fun setRepeatMode(mode: Int) = edit { it[Keys.REPEAT_MODE] = mode }
    suspend fun setPlaybackSpeed(speed: Float) =
        edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2f) }
    suspend fun setGapless(enabled: Boolean) = edit { it[Keys.GAPLESS] = enabled }
    suspend fun setPlayWithOthers(enabled: Boolean) = edit { it[Keys.PLAY_WITH_OTHERS] = enabled }
    suspend fun setBackgroundPlayback(enabled: Boolean) = edit { it[Keys.BACKGROUND_PLAYBACK] = enabled }
    suspend fun setCrossfadeEnabled(enabled: Boolean) = edit { it[Keys.CROSSFADE_ENABLED] = enabled }
    suspend fun setCrossfadeSeconds(seconds: Int) = edit { it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(1, 12) }
    suspend fun setSleepTimer(minutes: Int) = edit { it[Keys.SLEEP_TIMER_MIN] = minutes }
    suspend fun setLanguage(tag: String) = edit { it[Keys.LANGUAGE] = tag }
    suspend fun setColorOverrides(items: List<ColorItem>) =
        edit { it[Keys.COLOR_OVERRIDES] = items.joinToString(";") { "${it.mode}|${it.attr}|${it.value}" } }

    suspend fun toggleFavorite(songId: Long) {
        context.settingsDataStore.edit { p ->
            val current = p[Keys.FAVORITES].toIdSet()
            val next = if (songId in current) current - songId else current + songId
            p[Keys.FAVORITES] = next.joinToString(",")
        }
    }

    suspend fun hideSong(songId: Long) {
        context.settingsDataStore.edit { p ->
            val next = p[Keys.HIDDEN].toIdSet() + songId
            p[Keys.HIDDEN] = next.joinToString(",")
        }
    }

    suspend fun unhideSong(songId: Long) {
        context.settingsDataStore.edit { p ->
            val next = p[Keys.HIDDEN].toIdSet() - songId
            p[Keys.HIDDEN] = next.joinToString(",")
        }
    }

    suspend fun setLibrarySortMode(mode: Int) = edit { it[Keys.LIBRARY_SORT_MODE] = mode }
    suspend fun setLibraryTab(tab: Int) = edit { it[Keys.LIBRARY_TAB] = tab }
    suspend fun setPlaylistSortMode(mode: Int) = edit { it[Keys.PLAYLIST_SORT_MODE] = mode }
    suspend fun setShuffleOrder(order: String) = edit { it[Keys.SHUFFLE_ORDER] = order }
    suspend fun setPlaylistDetailSortMode(playlistId: Long, mode: Int) =
        edit { it[playlistSortKey(playlistId)] = mode }

    suspend fun setLastScanAt(ms: Long) = edit { it[Keys.LAST_SCAN_AT] = ms }

    suspend fun addPlayTime(ms: Long) {
        if (ms <= 0) return
        context.settingsDataStore.edit { p ->
            p[Keys.TOTAL_PLAY_MS] = (p[Keys.TOTAL_PLAY_MS] ?: 0L) + ms
        }
    }

    suspend fun setNotificationPlayback(enabled: Boolean) = edit { it[Keys.NOTIF_PLAYBACK] = enabled }
    suspend fun setNotificationLockScreen(enabled: Boolean) = edit { it[Keys.NOTIF_LOCKSCREEN] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private fun String?.toIdSet(): Set<Long> = this
        ?.split(',')
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        ?: emptySet()

    private fun String.parseColors(): List<ColorItem> = split(";")
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val pieces = part.split("|")
            if (pieces.size == 3) {
                runCatching { ColorItem(pieces[0].toInt(), pieces[1], pieces[2]) }.getOrNull()
            } else null
        }
}
