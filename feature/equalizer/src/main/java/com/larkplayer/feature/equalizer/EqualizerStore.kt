package com.larkplayer.feature.equalizer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.equalizerDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "equalizer_settings")

/** Equalizer settings survive app restarts independently of the player module. */
@Singleton
class EqualizerStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Saved(
        val enabled: Boolean,
        val presetId: Int,
        val bandCount: Int,
        val levels: IntArray,
    )

    private object Keys {
        val ENABLED = booleanPreferencesKey("eq_enabled")
        val PRESET = intPreferencesKey("eq_preset")
        val BAND_COUNT = intPreferencesKey("eq_band_count")
        val LEVELS = stringPreferencesKey("eq_levels")
    }

    suspend fun load(): Saved {
        val prefs = context.equalizerDataStore.data.first()
        val count = prefs[Keys.BAND_COUNT] ?: EqPresets.CUTOFFS_5.size
        val stored = prefs[Keys.LEVELS].orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .map { it.coerceIn(EqPresets.MIN_LEVEL, EqPresets.MAX_LEVEL) }
            .toIntArray()
        val levels = when {
            stored.size == count && count > 0 -> stored
            count > 0 -> EqPresets.preset(prefs[Keys.PRESET] ?: 15).levelsFor(count)
            else -> IntArray(0)
        }
        return Saved(
            enabled = prefs[Keys.ENABLED] ?: false,
            presetId = prefs[Keys.PRESET] ?: 15,
            bandCount = count,
            levels = levels,
        )
    }

    suspend fun save(enabled: Boolean, presetId: Int, bandCount: Int, levels: IntArray) {
        context.equalizerDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = enabled
            prefs[Keys.PRESET] = presetId
            prefs[Keys.BAND_COUNT] = bandCount
            prefs[Keys.LEVELS] = levels.joinToString(",")
        }
    }
}
