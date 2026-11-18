package com.larkplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette

/** Cola de reproducción con controles de repetición y aleatorio siempre visibles. */
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentInt by viewModel.accent.collectAsStateWithLifecycle()
    val queue = viewModel.queue()
    val current = viewModel.currentIndex()
    val accentColor = Color(accentInt)

    LarkGradientBackground(accent = accentColor, strength = 0.30f) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(onClick = onBack)
                Spacer(Modifier.width(18.dp))
                Text(
                    "A continuación",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${queue.size} canciones · ${state.title}",
                color = ApplePalette.TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
            ) {
                itemsIndexed(queue) { index, item ->
                    val isCurrent = index == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { viewModel.playAtIndex(index) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF2A2A2E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF6F6F76), modifier = Modifier.size(20.dp))
                            AsyncImage(
                                model = item.mediaMetadata.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.mediaMetadata.title?.toString() ?: "Desconocido",
                                color = if (isCurrent) accentColor else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.mediaMetadata.artist?.toString() ?: "",
                                color = ApplePalette.TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent) {
                            Text("SONANDO", color = accentColor, fontSize = 9.sp, letterSpacing = 0.6.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }

            // Repeat / repeat-one / shuffle, always reachable while browsing the queue.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QueueToggle(
                    icon = Icons.Rounded.Repeat,
                    label = "Repetir",
                    active = state.repeatMode == Player.REPEAT_MODE_ALL,
                    accent = accentColor,
                    onClick = {
                        viewModel.setRepeatMode(
                            if (state.repeatMode == Player.REPEAT_MODE_ALL) {
                                Player.REPEAT_MODE_OFF
                            } else Player.REPEAT_MODE_ALL
                        )
                    },
                )
                QueueToggle(
                    icon = Icons.Rounded.RepeatOne,
                    label = "Repetir una",
                    active = state.repeatMode == Player.REPEAT_MODE_ONE,
                    accent = accentColor,
                    onClick = {
                        viewModel.setRepeatMode(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                                Player.REPEAT_MODE_OFF
                            } else Player.REPEAT_MODE_ONE
                        )
                    },
                )
                QueueToggle(
                    icon = Icons.Rounded.Shuffle,
                    label = "Aleatorio",
                    active = state.shuffle,
                    accent = accentColor,
                    onClick = viewModel::toggleShuffle,
                )
            }
        }
    }
}

@Composable
private fun QueueToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(
            icon = icon,
            contentDescription = label,
            onClick = onClick,
            size = 52.dp,
            iconSize = 26.dp,
            tint = if (active) accent else Color.White,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (active) accent else ApplePalette.TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
