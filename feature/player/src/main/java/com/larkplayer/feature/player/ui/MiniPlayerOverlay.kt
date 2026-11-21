package com.larkplayer.feature.player.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Controls where the floating mini-player is allowed to appear. */
object MiniPlayerOverlay {
    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun setVisible(visible: Boolean) {
        _visible.value = visible
    }
}
