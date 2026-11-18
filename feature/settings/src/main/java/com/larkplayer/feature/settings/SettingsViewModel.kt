package com.larkplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.model.ThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaybackSettings(
    val playWithOthers: Boolean = true,
    val backgroundPlayback: Boolean = true,
    val gapless: Boolean = true,
    val crossfade: Boolean = false,
    val crossfadeSeconds: Int = 4,
    val notificationPlayback: Boolean = true,
    val notificationLockScreen: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: SettingsDataStore,
) : ViewModel() {

    val themeConfig: StateFlow<ThemeConfig> = dataStore.themeConfig.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeConfig(),
    )

    val language: StateFlow<String> = dataStore.language.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), "es",
    )

    val totalPlayMs: StateFlow<Long> = dataStore.totalPlayMs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L,
    )

    val hiddenCount: StateFlow<Int> = dataStore.hiddenSongIds.map { it.size }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0,
    )

    val sleepTimerMinutes: StateFlow<Int> = dataStore.sleepTimerMinutes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0,
    )

    // `combine` supports up to 5 sources, so the notification flag is merged last.
    val playback: StateFlow<PlaybackSettings> = combine(
        combine(
            dataStore.playWithOthers,
            dataStore.backgroundPlayback,
            dataStore.gapless,
            dataStore.crossfadeEnabled,
            dataStore.crossfadeSeconds,
        ) { together, background, gapless, crossfade, seconds ->
            PlaybackSettings(
                playWithOthers = together,
                backgroundPlayback = background,
                gapless = gapless,
                crossfade = crossfade,
                crossfadeSeconds = seconds,
            )
        },
        dataStore.notificationPlayback,
    ) { base, notif -> base.copy(notificationPlayback = notif) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackSettings())

    val notificationLockScreen: StateFlow<Boolean> = dataStore.notificationLockScreen.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { dataStore.setDynamicColor(enabled) }
    fun setThemeMode(mode: Int) = viewModelScope.launch { dataStore.setThemeMode(mode) }
    fun setThemeSize(size: Int) = viewModelScope.launch { dataStore.setThemeSize(size) }
    fun setAccentColor(argb: Long) = viewModelScope.launch { dataStore.setAccentColor(argb) }
    fun setGapless(enabled: Boolean) = viewModelScope.launch { dataStore.setGapless(enabled) }
    fun setPlayWithOthers(enabled: Boolean) = viewModelScope.launch { dataStore.setPlayWithOthers(enabled) }
    fun setBackgroundPlayback(enabled: Boolean) = viewModelScope.launch { dataStore.setBackgroundPlayback(enabled) }
    fun setCrossfade(enabled: Boolean) = viewModelScope.launch { dataStore.setCrossfadeEnabled(enabled) }
    fun setCrossfadeSeconds(seconds: Int) = viewModelScope.launch { dataStore.setCrossfadeSeconds(seconds) }
    fun setLanguage(tag: String) = viewModelScope.launch { dataStore.setLanguage(tag) }
    fun setNotificationPlayback(enabled: Boolean) = viewModelScope.launch { dataStore.setNotificationPlayback(enabled) }
    fun setNotificationLockScreen(enabled: Boolean) = viewModelScope.launch { dataStore.setNotificationLockScreen(enabled) }
}
