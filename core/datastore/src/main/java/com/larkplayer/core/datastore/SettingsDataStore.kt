package com.larkplayer.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val GAPLESS = booleanPreferencesKey("gapless")
        val SLEEP_TIMER_MIN = intPreferencesKey("sleep_timer_min")
        val COLOR_OVERRIDES = stringPreferencesKey("color_overrides") // "mode|attr|value;..."
        val LANGUAGE = stringPreferencesKey("language")
    }

    val themeConfig: Flow<ThemeConfig> = context.settingsDataStore.data.map { p ->
        ThemeConfig(
            useDynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            themeMode = p[Keys.THEME_MODE] ?: 1,
            overrides = (p[Keys.COLOR_OVERRIDES] ?: "").parseColors(),
        )
    }

    val shuffle: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.SHUFFLE] ?: false }
    val repeatMode: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.REPEAT_MODE] ?: 0 }
    val gapless: Flow<Boolean> = context.settingsDataStore.data.map { p -> p[Keys.GAPLESS] ?: true }
    val sleepTimerMinutes: Flow<Int> = context.settingsDataStore.data.map { p -> p[Keys.SLEEP_TIMER_MIN] ?: 0 }
    val language: Flow<String?> = context.settingsDataStore.data.map { p -> p[Keys.LANGUAGE] }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setThemeMode(mode: Int) = edit { it[Keys.THEME_MODE] = mode }
    suspend fun setShuffle(enabled: Boolean) = edit { it[Keys.SHUFFLE] = enabled }
    suspend fun setRepeatMode(mode: Int) = edit { it[Keys.REPEAT_MODE] = mode }
    suspend fun setGapless(enabled: Boolean) = edit { it[Keys.GAPLESS] = enabled }
    suspend fun setSleepTimer(minutes: Int) = edit { it[Keys.SLEEP_TIMER_MIN] = minutes }
    suspend fun setLanguage(tag: String) = edit { it[Keys.LANGUAGE] = tag }
    suspend fun setColorOverrides(items: List<ColorItem>) =
        edit { it[Keys.COLOR_OVERRIDES] = items.joinToString(";") { "${it.mode}|${it.attr}|${it.value}" } }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private fun String.parseColors(): List<ColorItem> = split(";")
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val pieces = part.split("|")
            if (pieces.size == 3) {
                runCatching { ColorItem(pieces[0].toInt(), pieces[1], pieces[2]) }.getOrNull()
            } else null
        }
}
