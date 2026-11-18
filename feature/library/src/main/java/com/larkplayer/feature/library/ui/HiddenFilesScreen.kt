package com.larkplayer.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.theme.ApplePalette

/** Hidden files: songs hidden from the library, restorable at any time. */
@Composable
fun HiddenFilesScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(onClick = onBack)
            Spacer(Modifier.width(18.dp))
            Text("Archivos ocultos", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }

        if (state.hiddenSongs.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.VisibilityOff, null, tint = Color(0xFF5D5D64), modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(16.dp))
                Text("No hay archivos ocultos", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mantén pulsada una canción en la biblioteca y elige «Ocultar».",
                    color = ApplePalette.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
                items(state.hiddenSongs, key = { it.id }) { song ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SongArtwork(song, Modifier.size(50.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(song.artist, color = ApplePalette.TextSecondary, fontSize = 11.5.sp, maxLines = 1)
                        }
                        TextButton(onClick = { viewModel.unhideSong(song.id) }) {
                            Text("Mostrar", color = ApplePalette.Blue)
                        }
                    }
                }
            }
        }
    }
}
