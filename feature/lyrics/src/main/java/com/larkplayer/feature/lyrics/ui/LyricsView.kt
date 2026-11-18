package com.larkplayer.feature.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.lyrics.LrcParser
import com.larkplayer.feature.player.ui.PlayerViewModel

/** Scrolling synchronized lyrics with Apple Music styling. */
@Composable
fun LyricsScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val lyrics by lyricsViewModel.lyrics.collectAsStateWithLifecycle()

    LaunchedEffect(playerState.title) {
        if (playerState.title.isNotBlank()) lyricsViewModel.load(playerState.title)
    }

    val active = LrcParser.activeIndex(lyrics, playerState.position)
    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        if (active >= 0) runCatching { listState.animateScrollToItem(active, -220) }
    }

    Box(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        if (lyrics.lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No lyrics found for this song", color = ApplePalette.TextSecondary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = index == active
                    Text(
                        line.text,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        fontSize = if (isActive) 24.sp else 17.sp,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else Color.White.copy(0.42f),
                    )
                }
            }
        }
    }
}
