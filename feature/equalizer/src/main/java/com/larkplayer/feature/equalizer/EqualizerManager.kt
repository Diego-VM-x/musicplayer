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
        equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
    }

    fun bands(): Short = equalizer?.numberOfBands ?: 0
    fun bandLevelRange(): ShortArray = equalizer?.bandLevelRange ?: shortArrayOf(0, 0)
    fun setBandLevel(band: Short, level: Short) { equalizer?.setBandLevel(band, level) }
    fun getBandLevel(band: Short): Short = equalizer?.getBandLevel(band) ?: 0
    fun centerFreq(band: Short): Int = equalizer?.getCenterFreq(band) ?: 0
    fun presets(): List<String> = (0 until (equalizer?.numberOfPresets ?: 0)).map {
        equalizer?.getPresetName(it.toShort()) ?: ""
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
