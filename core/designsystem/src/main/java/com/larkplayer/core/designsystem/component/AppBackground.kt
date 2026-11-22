package com.larkplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** User-selected app background: a colour variant and/or an image. */
data class AppBackground(
    val imageUri: String? = null,
    val variantIndex: Int = 0,
)

val LocalAppBackground = compositionLocalOf { AppBackground() }

/** 20 background colours, index 0 is the original Lark near-black. */
object BackgroundVariants {
    val colors: List<Color> = listOf(
        Color(0xFF0A0A0B), // Lark original
        Color(0xFF10141C), // Medianoche
        Color(0xFF0B1F1A), // Verde bosque
        Color(0xFF1A1024), // Violeta
        Color(0xFF241016), // Vino
        Color(0xFF101B2E), // Azul profundo
        Color(0xFF2B1D0E), // Ámbar
        Color(0xFF0E2222), // Turquesa
        Color(0xFF1E1E24), // Grafito
        Color(0xFF2A1010), // Carmesí
        Color(0xFF101010), // Negro puro
        Color(0xFF14251A), // Esmeralda
        Color(0xFF1C1630), // Índigo
        Color(0xFF2E1C2E), // Ciruela
        Color(0xFF0C1E28), // Acero azul
        Color(0xFF2B2110), // Dorado
        Color(0xFF121820), // Pizarra
        Color(0xFF241820), // Marrón
        Color(0xFF0A1A14), // Menta
        Color(0xFF1A1220), // Amatista
    )

    val names: List<String> = listOf(
        "Original", "Medianoche", "Verde bosque", "Violeta", "Vino",
        "Azul profundo", "Ámbar", "Turquesa", "Grafito", "Carmesí",
        "Negro", "Esmeralda", "Índigo", "Ciruela", "Acero azul",
        "Dorado", "Pizarra", "Marrón", "Menta", "Amatista",
    )

    fun color(index: Int): Color = colors.getOrElse(index) { colors[0] }
}

/** Full-screen backdrop painted behind every screen. */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val background = LocalAppBackground.current
    val base = BackgroundVariants.color(background.variantIndex)
    Box(modifier.fillMaxSize().background(base)) {
        if (!background.imageUri.isNullOrBlank()) {
            AsyncImage(
                model = background.imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                base.copy(alpha = 0.42f),
                                base.copy(alpha = 0.78f),
                            )
                        )
                    ),
            )
        }
    }
}
