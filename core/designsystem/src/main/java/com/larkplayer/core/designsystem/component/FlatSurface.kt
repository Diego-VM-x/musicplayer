package com.larkplayer.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf

/**
 * When true, shared Lark components drop the glass treatment (gradients,
 * translucency, hairline borders and shadows) and render flat surfaces.
 *
 * Enabled for the playlist and settings routes only; the player keeps its
 * original look.
 */
val LocalFlatSurface = compositionLocalOf { false }
