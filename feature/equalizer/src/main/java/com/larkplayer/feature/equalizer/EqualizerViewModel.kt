package com.larkplayer.feature.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.larkplayer.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val player: ExoPlayer,
    private val equalizerManager: EqualizerManager,
    settings: SettingsDataStore,
) : ViewModel() {

    /** Same state object as the manager: presets, levels, band count and on/off. */
    val state: StateFlow<EqualizerManager.State> = equalizerManager.state

    /** The user's accent colour from SettingsDataStore.accentColor. */
    val accent: StateFlow<Int> = settings.accentColor
        .map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0xFF0A84FF.toInt())

    init {
        viewModelScope.launch { equalizerManager.restore() }
        // The audio session id may not exist yet when the screen opens.
        viewModelScope.launch {
            while (isActive) {
                val session = player.audioSessionId
                if (session != 0) equalizerManager.ensureAttached(session)
                delay(500)
            }
        }
    }

    fun setEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)

    fun selectPreset(presetId: Int) = equalizerManager.usePreset(presetId)

    fun setLevel(band: Int, level: Int) = equalizerManager.setBandLevel(band, level)
}
