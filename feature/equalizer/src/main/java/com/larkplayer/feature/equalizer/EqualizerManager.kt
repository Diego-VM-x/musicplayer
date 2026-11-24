package com.larkplayer.feature.equalizer

import android.content.Context
import android.media.audiofx.Equalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the real audio effect and the Media3 DSP mirror of the original presets. */
@Singleton
class EqualizerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: EqualizerStore,
) {
    data class State(
        val available: Boolean = false,
        val enabled: Boolean = false,
        val presetId: Int = 15,
        val bandCount: Int = EqPresets.CUTOFFS_5.size,
        val levels: IntArray = EqPresets.preset(15).levels5.copyOf(),
        val cutoffs: IntArray = EqPresets.CUTOFFS_5.copyOf(),
        val minLevel: Int = EqPresets.MIN_LEVEL,
        val maxLevel: Int = EqPresets.MAX_LEVEL,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var effect: Equalizer? = null
    private var attachedSession = -1
    private var restored = false

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Media3 AudioProcessor with the same bands; ready for a DefaultAudioSink. */
    val media3Processor: EqualizerAudioProcessor = EqualizerAudioProcessor()

    suspend fun restore() {
        if (restored) return
        restored = true
        val saved = runCatching { store.load() }.getOrNull() ?: return
        val count = if (saved.bandCount > 0) saved.bandCount else EqPresets.CUTOFFS_5.size
        _state.value = State(
            available = _state.value.available,
            enabled = saved.enabled,
            presetId = saved.presetId,
            bandCount = count,
            levels = saved.levels,
            cutoffs = EqPresets.cutoffsFor(count),
        )
        effect?.let {
            it.enabled = saved.enabled
            applyToEffect(it)
        }
        syncProcessor()
    }

    /** Attaches the platform effect to the ExoPlayer audio session (Lark's real path). */
    fun ensureAttached(sessionId: Int) {
        if (sessionId <= 0 || sessionId == attachedSession) return
        attachedSession = sessionId
        effect?.let { runCatching { it.release() } }
        effect = null

        runCatching {
            val created = Equalizer(0, sessionId)
            val count = created.numberOfBands.toInt().coerceAtLeast(1)
            val range = created.bandLevelRange
            val current = _state.value
            val levels = if (count == current.bandCount && current.levels.size == count) {
                current.levels.copyOf()
            } else {
                EqPresets.preset(current.presetId).levelsFor(count)
            }
            // audiofx reports centre frequencies in milliHz and levels in millibel;
            // Lark displays Hz and -15..15 units (100 millibel per unit).
            val cutoffs = (0 until count).map { i ->
                runCatching { created.getCenterFreq(i.toShort()) / 1000 }.getOrDefault(0)
            }.toIntArray().let { if (it.all { f -> f > 0 }) it else EqPresets.cutoffsFor(count) }

            _state.value = current.copy(
                available = true,
                bandCount = count,
                levels = levels,
                cutoffs = cutoffs,
                minLevel = (range.getOrNull(0)?.toInt() ?: -1_500) / 100,
                maxLevel = (range.getOrNull(1)?.toInt() ?: 1_500) / 100,
            )
            created.enabled = current.enabled
            applyToEffect(created)
            effect = created
        }.onFailure {
            _state.value = _state.value.copy(available = false)
        }
        syncProcessor()
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
        runCatching { effect?.enabled = enabled }
        syncProcessor()
        persist()
    }

    fun usePreset(presetId: Int) {
        val current = _state.value
        val preset = EqPresets.preset(presetId)
        _state.value = current.copy(presetId = preset.id, levels = preset.levelsFor(current.bandCount))
        effect?.let { applyToEffect(it) }
        syncProcessor()
        persist()
    }

    fun setBandLevel(index: Int, level: Int) {
        val current = _state.value
        if (index !in current.levels.indices) return
        val next = current.levels.copyOf()
        next[index] = level.coerceIn(current.minLevel, current.maxLevel)
        val matching = EqPresets.ALL.firstOrNull { it.levelsFor(current.bandCount).contentEquals(next) }
        _state.value = current.copy(levels = next, presetId = matching?.id ?: EqPresets.CUSTOM)
        effect?.let { applyToEffect(it) }
        syncProcessor()
        persist()
    }

    private fun applyToEffect(target: Equalizer) {
        val current = _state.value
        val range = runCatching { target.bandLevelRange }.getOrDefault(shortArrayOf(-1_500, 1_500))
        for (i in current.levels.indices) {
            val millibel = (current.levels[i] * 100).coerceIn(
                range.getOrNull(0)?.toInt() ?: -1_500,
                range.getOrNull(1)?.toInt() ?: 1_500,
            )
            runCatching { target.setBandLevel(i.toShort(), millibel.toShort()) }
        }
    }

    private fun syncProcessor() {
        val current = _state.value
        media3Processor.configureBands(current.enabled, current.cutoffs, current.levels)
    }

    private fun persist() {
        val current = _state.value
        scope.launch {
            runCatching {
                store.save(current.enabled, current.presetId, current.bandCount, current.levels)
            }
        }
    }

    /** Kept for completeness; the singleton normally lives as long as playback does. */
    fun release() {
        effect?.let { runCatching { it.release() } }
        effect = null
        attachedSession = -1
    }
}
