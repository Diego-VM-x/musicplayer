package com.larkplayer.feature.lyrics.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val offsetMs by lyricsViewModel.offset.collectAsStateWithLifecycle()

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

    // Manual offset corrects the residual early/late drift of a given file.
    val effectivePosition = (playerState.position + offsetMs).coerceAtLeast(0L)
    val active = if (lyrics.isSynced) {
        LrcParser.activeIndex(lyrics, effectivePosition)
    } else {
        LrcParser.activeIndexForPlain(lyrics, effectivePosition, playerState.duration)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(active, lyrics.songId) {
        if (active >= 0) {
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
                    val isActive = active >= 0 && index == active
                    // Animated highlight: the active line grows, brightens and
                    // keeps a subtle glow while the others fade into the background.
                    val scale by animateFloatAsState(
                        targetValue = if (isActive) 1.12f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
                        label = "lyric_scale",
                    )
                    val lineAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.42f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 260f),
                        label = "lyric_alpha",
                    )
                    val lineColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 280f),
                        label = "lyric_color",
                    )
                    Text(
                        line.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (isActive) 12f else 0f
                            }
                            .alpha(lineAlpha),
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.Medium,
                        color = lineColor,
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

        // Manual sync trim: ±0.5 s per tap, remembered per song.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "−0.5s",
                color = ApplePalette.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { lyricsViewModel.adjustOffset(-500) }
                    .padding(8.dp),
            )
            Text(
                "${offsetMs} ms",
                color = ApplePalette.TextPrimary,
                fontSize = 12.sp,
            )
            Text(
                "+0.5s",
                color = ApplePalette.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { lyricsViewModel.adjustOffset(500) }
                    .padding(8.dp),
            )
        }
    }
}
