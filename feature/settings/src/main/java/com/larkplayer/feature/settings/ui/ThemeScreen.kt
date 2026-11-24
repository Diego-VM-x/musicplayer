package com.larkplayer.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

private fun parseHexColor(input: String): Color? {
    val raw = input.trim().removePrefix("#")
    if (raw.length != 6) return null
    val value = raw.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or value).toInt())
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
                Text("Elige cualquier tono con código #RRGGBB", color = Color(0xFF8E8E93), fontSize = 11.sp)
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
        var hex by remember {
            mutableStateOf(String.format("#%06X", accent.toArgb() and 0xFFFFFF))
        }
        val preview = parseHexColor(hex)
        AlertDialog(
            onDismissRequest = { customDialog = false },
            containerColor = ApplePalette.SurfaceElevated,
            title = { Text("Color personalizado", color = Color.White) },
            text = {
                Column {
                    Text("Escribe un código hexadecimal para elegir cualquier color.", color = Color(0xFFB7B7BD), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it },
                        singleLine = true,
                        label = { Text("#RRGGBB") },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(preview ?: accent)
                                .border(2.dp, Color.White, CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (preview != null) "Vista previa" else "Código no válido",
                            color = if (preview != null) Color.White else Color(0xFFFF9A9A),
                            fontSize = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview != null,
                    onClick = {
                        preview?.let { viewModel.setAccentColor(it.toArgbLong()) }
                        customDialog = false
                    },
                ) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { customDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

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
