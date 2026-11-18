package com.larkplayer.feature.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val player: ExoPlayer,
    private val equalizerManager: EqualizerManager,
) : ViewModel() {

    data class Band(val index: Short, val label: String, val level: Short, val min: Short, val max: Short)

    private val _bands = MutableStateFlow<List<Band>>(emptyList())
    val bands: StateFlow<List<Band>> = _bands.asStateFlow()

    private var attachedSession = -1

    init {
        // The audio session id may not exist yet when the screen opens, so the
        // equalizer is attached (and re-attached) as soon as it becomes available.
        viewModelScope.launch {
            while (isActive) {
                val session = player.audioSessionId
                if (session != 0 && session != attachedSession) {
                    equalizerManager.attach(session)
                    attachedSession = session
                }
                val values = computeBands()
                if (values.isNotEmpty() || _bands.value.isNotEmpty()) _bands.value = values
                delay(500)
            }
        }
    }

    private fun computeBands(): List<Band> {
        val count = equalizerManager.bands()
        val range = equalizerManager.bandLevelRange()
        if (count <= 0 || range.size < 2) return emptyList()
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

    fun setLevel(band: Short, level: Short) {
        equalizerManager.setBandLevel(band, level)
        _bands.value = computeBands()
    }

    private fun formatFreq(milliHz: Int): String = when {
        milliHz >= 1_000_000 -> "${milliHz / 1_000_000}k"
        milliHz >= 1_000 -> "${milliHz / 1_000}"
        else -> "$milliHz"
    }

    override fun onCleared() {
        equalizerManager.release()
        attachedSession = -1
        super.onCleared()
    }
}
