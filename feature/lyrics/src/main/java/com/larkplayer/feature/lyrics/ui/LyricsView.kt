package com.larkplayer.feature.lyrics.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.feature.lyrics.LrcParser
import com.larkplayer.feature.player.ui.PlayerViewModel

/** Scrolling synchronized lyrics — matches Lark's lyrics screen. */
@Composable
fun LyricsScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val lyrics = remember(state.title) {
        // In a full build this is loaded from a .lrc sidecar file via LyricsRepository.
        LyricsPlaceholder.forTitle(state.title)
    }
    val active = LrcParser.activeIndex(lyrics, state.position)
    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(active, -200)
    }

    Box(Modifier.fillMaxSize()) {
        if (lyrics.lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No lyrics available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Box
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isActive = index == active
                Text(
                    line.text,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = if (isActive) 22.sp else 16.sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                )
            }
        }
    }
}

private object LyricsPlaceholder {
    fun forTitle(title: String): com.larkplayer.core.model.Lyrics =
        com.larkplayer.core.model.Lyrics(songId = 0, lines = emptyList(), plainText = title)
}
