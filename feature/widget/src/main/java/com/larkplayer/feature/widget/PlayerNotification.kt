package com.larkplayer.feature.widget

import android.content.Context
import androidx.media3.session.MediaSessionService

/**
 * Media notifications and lock-screen controls are provided automatically by
 * MediaSessionService (in feature:player). This file documents where a custom
 * `MediaNotificationProvider` would be installed if a bespoke layout is needed.
 */
object PlayerNotification {
    fun installCustomProvider(service: MediaSessionService, context: Context) {
        // service.setMediaNotificationProvider(CustomProvider(context))
    }
}
