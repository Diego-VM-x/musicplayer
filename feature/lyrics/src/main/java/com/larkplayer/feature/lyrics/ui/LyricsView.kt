package com.larkplayer.feature.lyrics.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.model.LyricLine
import com.larkplayer.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.delay

/** Scrolling karaoke lyrics: word fill, frame-accurate sync, gentle auto-scroll. */
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

    // rememberUpdatedState keeps the single frame loop alive across seeks,
    // pauses and song changes while always reading the latest values.
    val latestPosition = rememberUpdatedState(playerState.position)
    val latestPlaying = rememberUpdatedState(playerState.isPlaying)
    val latestSpeed = rememberUpdatedState(playerState.playbackSpeed)
    val latestPulseEnabled = rememberUpdatedState(playerState.visualizerEnabled)
    val latestSongKey = rememberUpdatedState(songKey)
    val latestLyrics = rememberUpdatedState(lyrics)
    val latestOffset = rememberUpdatedState(offsetMs)

    val clock = remember { LyricsPlaybackClock() }
    LaunchedEffect(Unit) {
        clock.run(
            tickerPositionMs = { latestPosition.value },
            isPlaying = { latestPlaying.value },
            playbackSpeed = { latestSpeed.value },
            songKey = { latestSongKey.value },
            lyrics = { latestLyrics.value },
            offsetMs = { latestOffset.value },
            pulseEnabled = { latestPulseEnabled.value },
        )
    }

    val active = clock.activeLine.value
    val listState = rememberLazyListState()
    val suppressAutoScrollUntil = remember { mutableLongStateOf(0L) }

    // Manual drag wins for a short while; the timer is coarse on purpose so a
    // fling is covered and the effect is not restarted on every drag frame.
    fun noteManualScroll() {
        val now = SystemClock.elapsedRealtime()
        val next = ((now + MANUAL_SCROLL_PAUSE_MS) / 250L) * 250L
        if (next > suppressAutoScrollUntil.value) suppressAutoScrollUntil.value = next
    }

    LaunchedEffect(active, lyrics.songId, suppressAutoScrollUntil.value) {
        val wait = suppressAutoScrollUntil.value - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        if (active >= 0) runCatching { listState.animateScrollToItem(active, -180) }
    }

    Box(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        if (lyrics.hasText) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 92.dp, bottom = 96.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { noteManualScroll() },
                            onVerticalDrag = { _, _ -> noteManualScroll() },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    LyricLineRow(
                        line = line,
                        isActive = active == index && line.timestampMs >= 0,
                        clock = clock,
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

        // Honest state for embedded untimed text: no fake highlight, no drift.
        if (lyrics.hasText && !lyrics.isSynced) {
            Text(
                "Letra sin sincronización temporal",
                color = ApplePalette.TextSecondary.copy(alpha = 0.62f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp),
            )
        }

        CircleIconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )

        // Sync is derived automatically from the file/audio; no manual trim UI.
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    clock: LyricsPlaybackClock,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
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
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
    )

    // Read the clock in COMPOSITION, not only from the draw lambda. This makes
    // every interpolated frame invalidate this row (and therefore its karaoke
    // clip), instead of relying on draw-phase snapshot observation alone. Only
    // the active row subscribes, so the rest of the list is not recomposed 60×/s.
    val positionMs = if (isActive) clock.positionMs.value else 0L
    val pulse = if (isActive) clock.pulse.value else 0f

    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp)
        .graphicsLayer {
            // The optional energy pulse is subtle (max +2%) and never changes
            // the timing: the value was already read during composition.
            val animated = scale * (1f + 0.02f * pulse)
            scaleX = animated
            scaleY = animated
            shadowElevation = if (isActive) 10f else 0f
        }
        .alpha(lineAlpha)

    if (line.words.isNotEmpty() && line.timestampMs >= 0) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.Center,
        ) {
            line.words.forEachIndexed { index, word ->
                Box {
                    Text(
                        word.text,
                        style = style,
                        color = if (isActive) Color.White.copy(alpha = 0.42f) else Color.White,
                    )
                    if (isActive) {
                        // Progress is a plain Float captured from composition.
                        // The draw lambda no longer reads clock state, so the
                        // clip is guaranteed to follow the recomposed value.
                        val progress = clock.wordProgress(line, index, positionMs)
                        Text(
                            word.text,
                            style = style,
                            color = lineColor,
                            modifier = Modifier.drawWithContent {
                                if (progress > 0f) {
                                    drawContext.canvas.save()
                                    drawContext.canvas.clipRect(
                                        0f, 0f, size.width * progress, size.height,
                                    )
                                    drawContent()
                                    drawContext.canvas.restore()
                                }
                            },
                        )
                    }
                }
            }
        }
    } else {
        Text(line.text, modifier = rowModifier, style = style, color = lineColor)
    }
}

private const val MANUAL_SCROLL_PAUSE_MS = 2_500L
