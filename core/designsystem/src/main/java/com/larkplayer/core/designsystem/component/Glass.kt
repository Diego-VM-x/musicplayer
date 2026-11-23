package com.larkplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flat matte surface — no glass, no gradient, no border, no shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = Color.White,
    alpha: Float = 0.10f,
    borderAlpha: Float = 0.22f,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1E1F23)),
        content = content,
    )
}

/** Flat solid backdrop — replaces blurred album art with matte dark surface. */
@Composable
fun BlurredBackdrop(
    model: Any?,
    modifier: Modifier = Modifier,
    overlay: Color = Color.Black.copy(0.45f),
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1A1A1E)))
}

/** Flat solid navigation bar. */
@Composable
fun GlassNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(72.dp),
        color = Color(0xFF1A1A1E),
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            tonalElevation = 0.dp,
        ) { content() }
    }
}

@Composable
fun glassNavItemColors(selected: Color, unselected: Color) = NavigationBarItemDefaults.colors(
    selectedIconColor = selected,
    selectedTextColor = selected,
    unselectedIconColor = unselected,
    unselectedTextColor = unselected,
    indicatorColor = Color.White.copy(0.12f),
)

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(50),
        alpha = 0.08f,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}
