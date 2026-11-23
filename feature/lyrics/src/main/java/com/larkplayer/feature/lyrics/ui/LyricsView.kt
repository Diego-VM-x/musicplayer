package com.larkplayer.feature.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.lyrics.LrcParser
import com.larkplayer.feature.player.ui.PlayerViewModel

/** Scrolling lyrics: synchronized lines are highlighted, plain text just scrolls. */
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val lyrics by lyricsViewModel.lyrics.collectAsStateWithLifecycle()

    // Reload when the track identity changes, not only when the title changes:
    // the controller can be reattached with the same title but a different id.
    val songKey = "${playerState.id}|${playerState.title}|${playerState.songPath}"
    LaunchedEffect(songKey) {
        if (playerState.id > 0 || playerState.title.isNotBlank()) {
            lyricsViewModel.load(
                title = playerState.title,
                artist = playerState.artist,
                path = playerState.songPath,
                songId = playerState.id,
            )
        }
    }

    val active = if (lyrics.isSynced) LrcParser.activeIndex(lyrics, playerState.position) else -1
    val listState = rememberLazyListState()

    // Smooth auto-scroll only for synchronized lyrics; plain text is user-driven.
    LaunchedEffect(active, lyrics.songId) {
        if (lyrics.isSynced && active >= 0) {
            runCatching { listState.animateScrollToItem(active, -180) }
        }
    }

    Box(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        if (lyrics.hasText) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 92.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = lyrics.isSynced && index == active
                    Text(
                        line.text,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        fontSize = when {
                            isActive -> 24.sp
                            lyrics.isSynced -> 17.sp
                            else -> 18.sp
                        },
                        color = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            lyrics.isSynced -> Color.White.copy(0.42f)
                            else -> Color.White.copy(0.85f)
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Sin letras disponibles",
                    color = ApplePalette.TextPrimary,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Esta canción no tiene letras incrustadas ni un archivo .lrc o .txt junto al audio.",
                    color = ApplePalette.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        CircleIconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
    }
}
