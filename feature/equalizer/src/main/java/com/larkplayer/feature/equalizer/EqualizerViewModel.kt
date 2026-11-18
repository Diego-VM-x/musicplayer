package com.larkplayer.feature.equalizer

import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val player: ExoPlayer,
    private val equalizerManager: EqualizerManager,
) : ViewModel() {

    data class Band(val index: Short, val label: String, val level: Short, val min: Short, val max: Short)

    private var attached = false

    fun ensureAttached() {
        if (attached) return
        equalizerManager.attach(player.audioSessionId)
        attached = true
    }

    fun bands(): List<Band> {
        ensureAttached()
        val count = equalizerManager.bands()
        val range = equalizerManager.bandLevelRange()
        return (0 until count).map { i ->
            val band = i.toShort()
            Band(
                index = band,
                label = formatFreq(equalizerManager.centerFreq(band)),
                level = equalizerManager.getBandLevel(band),
                min = range[0],
                max = range[1],
            )
        }
    }

    fun setLevel(band: Short, level: Short) = equalizerManager.setBandLevel(band, level)

    private fun formatFreq(milliHz: Int): String = when {
        milliHz >= 1_000_000 -> "${milliHz / 1_000_000}k"
        milliHz >= 1_000 -> "${milliHz / 1_000}"
        else -> "$milliHz"
    }

    override fun onCleared() {
        equalizerManager.release()
        super.onCleared()
    }
}
