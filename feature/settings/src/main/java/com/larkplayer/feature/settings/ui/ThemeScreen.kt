package com.larkplayer.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.BackgroundVariants
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.model.ThemeConfig
import com.larkplayer.feature.player.ui.MiniPlayerPreview
import com.larkplayer.feature.settings.SettingsViewModel

private data class ThemeSizeOption(val index: Int, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val SIZE_OPTIONS = listOf(
    ThemeSizeOption(0, "Pequeño", Icons.Rounded.ViewList),
    ThemeSizeOption(1, "Medio", Icons.Rounded.ViewAgenda),
    ThemeSizeOption(2, "Grande", Icons.Rounded.GridView),
)

/** 20 presets: five hues in four tones each, so every user can pick a shade. */
private val ACCENTS = listOf(
    // Azul
    Color(0xFF0A84FF), Color(0xFF64D2FF), Color(0xFF3D6FE0), Color(0xFF0A4FA8),
    // Verde
    Color(0xFF34C759), Color(0xFF6EE07A), Color(0xFF1FA845), Color(0xFF0E6B2E),
    // Rosa / rojo
    Color(0xFFE84A8A), Color(0xFFFF7BA8), Color(0xFFD63447), Color(0xFF8A1C4A),
    // Naranja / amarillo
    Color(0xFFFF9500), Color(0xFFFFC044), Color(0xFFD96A00), Color(0xFF9A4A00),
    // Morado
    Color(0xFFB08CFF), Color(0xFFD0B8FF), Color(0xFF7D5AE8), Color(0xFF4F35A8),
)

/** HSV (hue in degrees, saturation/value in 0..1) to ARGB Color. */
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = value * saturation
    val x = c * (1f - abs(h / 60f % 2f - 1f))
    val m = value - c
    val (r, g, b) = when ((h / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
    )
}

/** ARGB Color to HSV (hue in degrees, saturation/value in 0..1). */
private fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> ((60f * (g - b) / delta) % 360f + 360f) % 360f
        max == g -> 60f * (b - r) / delta + 120f
        else -> 60f * (r - g) / delta + 240f
    }
    val saturation = if (max == 0f) 0f else delta / max
    return Triple(hue, saturation, max)
}

/** "Tema" — live previews, light/dark/system selector and accent colours. */
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.themeConfig.collectAsStateWithLifecycle(ThemeConfig())
    val accent = Color(config.accentColor.toInt())
    var customDialog by remember { mutableStateOf(false) }
    val dark = config.themeMode != 0
    val context = LocalContext.current
    val bgUri by viewModel.appBackgroundUri.collectAsStateWithLifecycle("")
    val bgVariant by viewModel.backgroundVariant.collectAsStateWithLifecycle(0)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.setAppBackgroundImage(context, uri)
    }

    Column(Modifier.fillMaxSize().background(Color.Transparent).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(onClick = onBack)
            Spacer(Modifier.width(20.dp))
            Text(
                "Tema",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            SIZE_OPTIONS.forEach { option ->
                SizeOption(
                    option = option,
                    selected = config.themeSize == option.index,
                    accent = accent,
                    onClick = { viewModel.setThemeSize(option.index) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ThemePreviewCard(kind = 0, size = config.themeSize, accent = accent, dark = dark) }
            item { ThemePreviewCard(kind = 1, size = config.themeSize, accent = accent, dark = dark) }
        }

        Spacer(Modifier.height(22.dp))

        LarkSegmented(
            options = listOf(
                Triple("Oscuro", Icons.Rounded.DarkMode, config.themeMode == 1),
                Triple("Claro", Icons.Rounded.LightMode, config.themeMode == 0),
                Triple("Sistema", Icons.Rounded.BrightnessAuto, config.themeMode == 2),
            ),
            onSelect = { index ->
                viewModel.setThemeMode(when (index) { 0 -> 1; 1 -> 0; else -> 2 })
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            accent = accent,
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "Color de acento",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))

        ACCENTS.chunked(5).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { color ->
                    AccentCircle(
                        color = color,
                        selected = color.toArgb() == accent.toArgb(),
                        accent = accent,
                    ) { viewModel.setAccentColor(color.toArgbLong()) }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B1B1E))
                .border(1.dp, Color(0xFF2D2D31), RoundedCornerShape(16.dp))
                .clickable { customDialog = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Palette,
                "Color personalizado",
                tint = Color.White,
                modifier = Modifier.padding(start = 14.dp).size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Color personalizado", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Elige cualquier tono con la rueda de colores", color = Color(0xFF8E8E93), fontSize = 11.sp)
            }
            Box(Modifier.padding(end = 14.dp).size(24.dp).clip(CircleShape).background(accent))
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Fondo de la app",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent)
                .clickable { imagePicker.launch("image/*") },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Cambiar fondo de la app",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (bgUri.isNotBlank()) {
            Text(
                "Quitar imagen de fondo",
                color = Color(0xFFFF9A9A),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { viewModel.clearAppBackgroundImage(context) },
            )
        }
        Spacer(Modifier.height(14.dp))
        BackgroundVariants.colors.chunked(5).forEachIndexed { rowIndex, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEachIndexed { columnIndex, color ->
                    val index = rowIndex * 5 + columnIndex
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (index == bgVariant) 3.dp else 1.dp,
                                color = if (index == bgVariant) Color.White else Color.White.copy(alpha = 0.16f),
                                shape = CircleShape,
                            )
                            .clickable { viewModel.setBackgroundVariant(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == bgVariant) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(20.dp))
    }

    if (customDialog) {
        ColorWheelDialog(
            initialColor = accent,
            onDismiss = { customDialog = false },
            onApply = { color ->
                viewModel.setAccentColor(color.toArgbLong())
                customDialog = false
            },
        )
    }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/**
 * Photo-editor style colour picker: the disc maps hue to the pointer angle and
 * saturation to the distance from the centre; a separate bar controls value
 * (brightness). Everything is drawn with Compose Canvas and converted to ARGB
 * before being handed back to the caller.
 */
@Composable
private fun ColorWheelDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
) {
    val initial = remember { rgbToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initial.first) }
    var saturation by remember { mutableFloatStateOf(initial.second) }
    var value by remember { mutableFloatStateOf(initial.third) }
    val color = hsvToColor(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApplePalette.SurfaceElevated,
        title = { Text("Color personalizado", color = Color.White) },
        text = {
            Column {
                Text(
                    "Arrastra el círculo para elegir matiz y saturación, y usa la barra para ajustar el brillo.",
                    color = Color(0xFFB7B7BD),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Canvas(
                    Modifier
                        .size(208.dp)
                        .align(Alignment.CenterHorizontally)
                        .pointerInput(Unit) {
                            val radius = minOf(size.width, size.height) / 2f
                            val apply: (Offset) -> Unit = { offset ->
                                val dx = offset.x - radius
                                val dy = offset.y - radius
                                saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
                                val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                hue = ((degrees % 360f) + 360f) % 360f
                            }
                            detectTapGestures(onTap = apply)
                        }
                        .pointerInput(Unit) {
                            val radius = minOf(size.width, size.height) / 2f
                            val apply: (Offset) -> Unit = { offset ->
                                val dx = offset.x - radius
                                val dy = offset.y - radius
                                saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
                                val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                hue = ((degrees % 360f) + 360f) % 360f
                            }
                            detectDragGestures(
                                onDragStart = apply,
                                onDrag = { change, _ ->
                                    change.consume()
                                    apply(change.position)
                                },
                            )
                        }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f

                    // Hue cone: one wedge per slice, in clockwise screen-angle order.
                    val step = 1.5f
                    var angle = 0f
                    while (angle < 360f) {
                        drawArc(
                            color = hsvToColor(angle, 1f, 1f),
                            startAngle = angle,
                            sweepAngle = step + 0.6f,
                            useCenter = true,
                        )
                        angle += step
                    }
                    // Saturation: opaque white at the centre fading to nothing at the rim.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.28f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )

                    // Draggable marker at (hue angle, saturation radius).
                    val radians = Math.toRadians(hue.toDouble())
                    val marker = Offset(
                        center.x + radius * saturation * cos(radians).toFloat(),
                        center.y + radius * saturation * sin(radians).toFloat(),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 10.dp.toPx(),
                        center = marker,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(color = color, radius = 8.dp.toPx(), center = marker)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = marker,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Brillo", color = Color(0xFFB7B7BD), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            val width = size.width.toFloat()
                            val apply: (Offset) -> Unit = { offset ->
                                value = (offset.x / width).coerceIn(0f, 1f)
                            }
                            detectTapGestures(onTap = apply)
                        }
                        .pointerInput(Unit) {
                            val width = size.width.toFloat()
                            detectDragGestures(
                                onDragStart = { offset -> value = (offset.x / width).coerceIn(0f, 1f) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    value = (change.position.x / width).coerceIn(0f, 1f)
                                },
                            )
                        }
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Black, hsvToColor(hue, saturation, 1f)),
                        ),
                    )
                    val x = value * size.width
                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, Color.White, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Vista previa",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Matiz ${hue.toInt()}°  ·  Saturación ${(saturation * 100).toInt()}%  ·  Brillo ${(value * 100).toInt()}%",
                            color = Color(0xFF8E8E93),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(color) }) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun SizeOption(
    option: ThemeSizeOption,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 12.dp else 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            option.icon,
            option.label,
            tint = if (selected) Color.Black else Color(0xFFB7B7BD),
            modifier = Modifier.size(19.dp),
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(option.label, color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccentCircle(
    color: Color,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, "Seleccionado", tint = Color.Black, modifier = Modifier.size(20.dp))
        }
    }
}

// ---- Live theme previews ----------------------------------------------------

private data class PreviewSong(val title: String, val artist: String, val color: Color)

private val PREVIEW_SONGS = listOf(
    PreviewSong("Crazy In Love With Your Smile", "Don Juan", Color(0xFF6E8F4C)),
    PreviewSong("Heart Skips A Beat", "Haneri", Color(0xFFFF5A25)),
    PreviewSong("Beach Memories", "Nattan", Color(0xFF2E6F8E)),
    PreviewSong("Can't Stop This Feeling", "Henrique & Juliano", Color(0xFF3A3A40)),
    PreviewSong("Blue Eyes That Saw My Soul", "MC Peoze do Rodo", Color(0xFF2B6A8A)),
    PreviewSong("Touch The Sky", "Don Juan", Color(0xFF2D5B9A)),
)

private val PREVIEW_PEOPLE = listOf(
    PreviewSong("Nattan", "MC Peoze do Rodo", Color(0xFFB2543A)),
    PreviewSong("Zé Felipe", "Tem Cabaré essa...", Color(0xFF8A4A3A)),
    PreviewSong("MC Cabelinho", "Maçã do Rosto", Color(0xFF4A6E3A)),
    PreviewSong("Dj Chris", "Algo íntimo", Color(0xFF5A3A6E)),
)

@Composable
private fun ThemePreviewCard(
    kind: Int,
    size: Int,
    accent: Color,
    dark: Boolean,
) {
    val bg = if (dark) Color(0xFF101013) else Color(0xFFF4F4F7)
    val card = if (dark) Color(0xFF1A1A1D) else Color.White
    val text = if (dark) Color.White else Color(0xFF101013)
    val sub = if (dark) Color(0xFF8E8E96) else Color(0xFF7A7A80)

    Column(
        Modifier
            .width(272.dp)
            .height(470.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(1.dp, if (dark) Color(0xFF2B2B30) else Color(0xFFE1E1E6), RoundedCornerShape(24.dp))
            .padding(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lark Player", color = text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.Search, null, tint = text, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.SwapVert, null, tint = text, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.RadioButtonChecked, null, tint = text, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Videos", "Canciones", "Playlists", "Folders").forEachIndexed { i, label ->
                Text(
                    label,
                    color = if (i == 1) Color.Black else sub,
                    fontSize = 6.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (i == 1) ApplePalette.Blue else card)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (kind == 0) {
            when (size) {
                0 -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PREVIEW_SONGS.take(6).forEach { PreviewRow(it, 27, card, text, sub) }
                }
                2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PREVIEW_SONGS.chunked(2).take(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { song ->
                                Column(Modifier.weight(1f)) {
                                    Box(
                                        Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(8.dp))
                                            .background(Brush.linearGradient(listOf(song.color, song.color.copy(0.55f)))),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(0.55f), modifier = Modifier.size(18.dp))
                                    }
                                    Text(song.title, color = text, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 9.sp)
                                    Text(song.artist, color = sub, fontSize = 6.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PREVIEW_SONGS.take(5).forEach { PreviewRow(it, 34, card, text, sub) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                PREVIEW_PEOPLE.forEach { person ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(card)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(person.color, person.color.copy(0.4f)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(person.title.take(1), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(person.title, color = text, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("${person.artist} · 4", color = sub, fontSize = 6.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        MiniPlayerPreview(accent = accent)
    }
}

@Composable
private fun PreviewRow(
    song: PreviewSong,
    art: Int,
    card: Color,
    text: Color,
    sub: Color,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(card)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(art.dp).clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(song.color, song.color.copy(0.5f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(0.55f), modifier = Modifier.size((art / 2.5).dp))
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = text,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, color = sub, fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
