package com.larkplayer.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.LibraryRepository
import com.larkplayer.feature.player.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MemoriesViewModel @Inject constructor(
    repository: LibraryRepository,
    settings: SettingsDataStore,
    private val player: PlayerRepository,
) : ViewModel() {
    val topSongs = repository.topSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val totalPlayMs = settings.totalPlayMs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L,
    )

    fun play(songs: List<Song>, index: Int) = player.playSongs(songs, index)
}

/** "Recuerdos musicales": most played songs and total listening time. */
@Composable
fun MemoriesScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: MemoriesViewModel = hiltViewModel(),
) {
    val songs by viewModel.topSongs.collectAsStateWithLifecycle()
    val totalMs by viewModel.totalPlayMs.collectAsStateWithLifecycle()
    val minutes = totalMs / 60_000L

    Column(
        Modifier
            .fillMaxSize()
            .background(ApplePalette.Background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        CircleIconButton(onClick = onBack)
        Spacer(Modifier.height(18.dp))
        Text(
            "Recuerdos musicales",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tus canciones más reproducidas y el tiempo que llevas escuchando.",
            color = ApplePalette.TextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Tiempo de reproducción", color = ApplePalette.TextSecondary, fontSize = 11.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min",
                    color = ApplePalette.Blue,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Más reproducidas", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        if (songs.isEmpty()) {
            Text(
                "Aún no hay datos. Escucha algunas canciones para ver tus recuerdos.",
                color = ApplePalette.TextSecondary,
                fontSize = 12.5.sp,
            )
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)) {
                itemsIndexed(songs) { index, song ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .clickable {
                                viewModel.play(songs, index)
                                onOpenPlayer()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ApplePalette.Blue.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                color = ApplePalette.Blue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                color = ApplePalette.TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Rounded.MusicNote,
                            null,
                            tint = Color(0xFF8E8E95),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
