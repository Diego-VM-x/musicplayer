package com.larkplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.model.ThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: SettingsDataStore,
) : ViewModel() {
    val themeConfig: StateFlow<ThemeConfig> = dataStore.themeConfig.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeConfig(),
    )

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        dataStore.setDynamicColor(enabled)
    }
    fun setThemeMode(mode: Int) = viewModelScope.launch { dataStore.setThemeMode(mode) }
}
