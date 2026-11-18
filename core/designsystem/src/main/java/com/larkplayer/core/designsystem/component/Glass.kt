package com.larkplayer.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Liquid-glass surface: translucent gradient fill, luminous hairline border
 * and soft shadow. No per-card RenderEffect blur, so it stays optimized.
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
    val fill = Brush.linearGradient(
        colors = listOf(
            tint.copy(alpha = alpha * 1.4f),
            tint.copy(alpha = alpha * 0.55f),
            tint.copy(alpha = alpha * 0.18f),
        )
    )
    val stroke = Brush.linearGradient(
        colors = listOf(
            tint.copy(alpha = borderAlpha),
            tint.copy(alpha = borderAlpha * 0.25f),
            tint.copy(alpha = borderAlpha * 0.7f),
        )
    )
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, stroke), shape),
        content = content,
    )
}

/** Blurred, darkened album-art backdrop for hero / player screens. */
@Composable
fun BlurredBackdrop(
    model: Any?,
    modifier: Modifier = Modifier,
    overlay: Color = Color.Black.copy(0.45f),
) {
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, overlay))),
        )
    }
}

/** Translucent glass navigation bar. */
@Composable
fun GlassNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(72.dp),
        color = Color(0x14000000),
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
