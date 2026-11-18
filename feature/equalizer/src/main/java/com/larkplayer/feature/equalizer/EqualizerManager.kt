package com.larkplayer.feature.equalizer

import android.media.audiofx.Equalizer
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the platform audiofx Equalizer — no third-party library needed. */
@Singleton
class EqualizerManager @Inject constructor() {
    private var equalizer: Equalizer? = null

    fun attach(audioSessionId: Int) {
        release()
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
        }.onFailure {
            equalizer = null
        }
    }

    fun bands(): Short = runCatching { equalizer?.numberOfBands ?: 0 }.getOrDefault(0)
    fun bandLevelRange(): ShortArray =
        runCatching { equalizer?.bandLevelRange ?: shortArrayOf(0, 0) }.getOrDefault(shortArrayOf(0, 0))
    fun setBandLevel(band: Short, level: Short) {
        runCatching { equalizer?.setBandLevel(band, level) }
    }
    fun getBandLevel(band: Short): Short =
        runCatching { equalizer?.getBandLevel(band) ?: 0 }.getOrDefault(0)
    fun centerFreq(band: Short): Int =
        runCatching { equalizer?.getCenterFreq(band) ?: 0 }.getOrDefault(0)
    fun presets(): List<String> = runCatching {
        (0 until (equalizer?.numberOfPresets ?: 0)).map {
            equalizer?.getPresetName(it.toShort()) ?: ""
        }
    }.getOrDefault(emptyList())

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
