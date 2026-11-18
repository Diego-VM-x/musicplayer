package com.larkplayer.core.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-module signal to request a MediaStore rescan.
 * Emitted by MainActivity after permissions are granted / on resume,
 * collected by LibraryViewModel.
 */
@Singleton
class MediaScanBus @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun requestScan() { _events.tryEmit(Unit) }
}
