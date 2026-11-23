package com.larkplayer.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf

/**
 * When true, shared Lark components drop the glass treatment (gradients,
 * translucency, hairline borders and shadows) and render flat surfaces.
 *
 * Always true — flat matte aesthetic everywhere, no liquid glass anywhere.
 */
val LocalFlatSurface = compositionLocalOf { true }
