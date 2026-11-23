package com.larkplayer.feature.player.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.larkplayer.core.designsystem.R as DesignR
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.common.RingtoneHelper
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassMenu
import com.larkplayer.core.designsystem.component.GlassMenuItem
import com.larkplayer.core.designsystem.component.GlassPill
import com.larkplayer.core.designsystem.component.LarkDialog
import com.larkplayer.core.designsystem.component.LarkSheet
import com.larkplayer.core.designsystem.component.LarkTextField
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.player.PlayerRepository
import java.util.Locale
import kotlin.math.abs

/**
 * Lark's now-playing screen, aligned with `fragment_player_motion_audio.xml`:
 * no top bar, swipe-down to close, white two-line title, three transport
 * controls, and the EQ / Letras / queue row with the mode badge on the queue.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onLyrics: () -> Unit = {},
    onQueue: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentInt by viewModel.accent.collectAsStateWithLifecycle()
    val accent = Color(accentInt)
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // Keep the screen on while a track plays (was previously created at app start).
    DisposableEffect(state.isPlaying) {
        val activity = context as? Activity
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (state.isPlaying) activity?.window?.addFlags(flag)
        else activity?.window?.clearFlags(flag)
        onDispose { activity?.window?.clearFlags(flag) }
    }

    var sliderValue by remember(state.id) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var visualizerSheetOpen by remember { mutableStateOf(false) }
    var dragDown by remember { mutableFloatStateOf(0f) }
    var playlistPickerOpen by remember { mutableStateOf(false) }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var ringtoneWaitingPermission by remember { mutableStateOf(false) }

    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (ringtoneWaitingPermission && Settings.System.canWrite(context)) {
            val song = viewModel.currentSong()
            if (song != null) {
                val ok = RingtoneHelper.set(context, song)
                Toast.makeText(
                    context,
                    if (ok) "Tono de llamada actualizado" else "No se pudo cambiar el tono",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        ringtoneWaitingPermission = false
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.hideCurrent()
            viewModel.skipNext()
            Toast.makeText(context, "Canción borrada", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareCurrent() {
        val song = viewModel.currentSong() ?: return
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, MediaFileUtils.songUri(song))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir canción"))
        }.onFailure {
            Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestRingtone() {
        val song = viewModel.currentSong() ?: return
        if (Settings.System.canWrite(context)) {
            val ok = RingtoneHelper.set(context, song)
            Toast.makeText(
                context,
                if (ok) "Tono de llamada actualizado" else "No se pudo cambiar el tono",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            ringtoneWaitingPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { writeSettingsLauncher.launch(intent) }.onFailure {
                ringtoneWaitingPermission = false
                Toast.makeText(context, "Activa el permiso de tonos manualmente", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun requestDelete() {
        val song = viewModel.currentSong() ?: return
        val uri = MediaFileUtils.songUri(song)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure {
                Toast.makeText(context, "No se pudo borrar la canción", Toast.LENGTH_SHORT).show()
            }
        } else {
            val ok = runCatching { context.contentResolver.delete(uri, null, null) }.isSuccess
            if (ok) {
                viewModel.hideCurrent()
                viewModel.skipNext()
                Toast.makeText(context, "Canción borrada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No se pudo borrar la canción", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(state.position, dragging) {
        if (!dragging) sliderValue = state.position.toFloat()
    }

    val currentMode = viewModel.currentPlaybackMode()
    val speedVisible = abs(state.playbackSpeed - 1f) > 0.01f

    // Solid colour derived from the current artwork, used only by the player.
    val playerBackground = androidx.compose.ui.graphics.lerp(
        Color(0xFF101014),
        accent,
        0.55f,
    )
    NavBarTint(color = playerBackground, active = true)

    Box(
        Modifier
            .fillMaxSize()
            .background(playerBackground)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Drag-down handle ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragDown > 90f) onBack()
                                dragDown = 0f
                            },
                            onDragCancel = { dragDown = 0f },
                        ) { _, dragAmount ->
                            dragDown += dragAmount
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Invisible touch area or subtle indicator if needed
            }

            // ---- Artwork (Lark Player original: rounded square, pure matte) ----
            var dragDistance by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragDistance > 60f) {
                                    viewModel.skipPrevious()
                                } else if (dragDistance < -60f) {
                                    viewModel.skipNext()
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            dragDistance += dragAmount
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = state.id,
                    transitionSpec = {
                        (slideInHorizontally(tween(260)) { width -> width / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(200)) { width -> -width / 3 } + fadeOut(tween(200)))
                    },
                    label = "artwork_slide_transition",
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            null,
                            tint = Color(0xFF4A4A4A),
                            modifier = Modifier.size(64.dp),
                        )
                        AsyncImage(
                            model = state.albumArt,
                            contentDescription = "Carátula",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Title + artist, with favourite and overflow on the right (Lark exact match) ----
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = state.title,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 }) togetherWith
                                (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 4 })
                        },
                        label = "title_transition",
                    ) { titleText ->
                        Text(
                            titleText.uppercase(Locale.getDefault()),
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.sp,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        (state.artist.ifBlank { "Desconocido" }).uppercase(Locale.getDefault()),
                        color = Color(0xFF8E8E93),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Favorite heart icon (Lark exact outline/filled)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = viewModel::toggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.isFavorite) DesignR.drawable.ic_lark_heart_filled
                            else DesignR.drawable.ic_lark_heart_outline
                        ),
                        contentDescription = if (state.isFavorite) "Quitar de favoritos" else "Favorito",
                        tint = if (state.isFavorite) Color(0xFFFF4D67) else Color(0xFFE0E0E0),
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(4.dp))

                // More 3-dots icon (Lark exact)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_lark_more),
                        contentDescription = "Más opciones",
                        tint = Color(0xFFE0E0E0),
                        modifier = Modifier.size(24.dp),
                    )
                }

                if (menuOpen) {
                    PlayerMoreSheet(
                        title = state.title,
                        artist = state.artist,
                        albumArt = state.albumArt,
                        onAddToPlaylist = {
                            menuOpen = false
                            playlistPickerOpen = true
                        },
                        onShare = {
                            menuOpen = false
                            shareCurrent()
                        },
                        onVisualizer = {
                            menuOpen = false
                            visualizerSheetOpen = true
                        },
                        onSpeed = {
                            menuOpen = false
                            speedMenu = true
                        },
                        onSetRingtone = {
                            menuOpen = false
                            requestRingtone()
                        },
                        onSleepTimer = {
                            menuOpen = false
                            onSleepTimer()
                        },
                        onHide = {
                            menuOpen = false
                            viewModel.hideCurrent()
                            Toast.makeText(context, "Canción oculta de la biblioteca", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            menuOpen = false
                            confirmDelete = true
                        },
                        onDismissRequest = { menuOpen = false },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Visualizador: solo se dibuja si el usuario lo activa ----
            if (state.visualizerEnabled) {
                VisualizerCanvas(
                    enabled = true,
                    isPlaying = state.isPlaying,
                    style = state.visualizerStyle,
                    accent = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clickable { visualizerSheetOpen = true },
                )
            }

            // ---- Barra de progreso y tiempos ----
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue.coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = {
                        dragging = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        viewModel.seek(sliderValue.toLong())
                        dragging = false
                    },
                    valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFE0E0E0),
                        inactiveTrackColor = Color(0xFF444444),
                    ),
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatTime(state.position),
                        color = Color(0xFF8E8E93),
                        fontSize = 11.5.sp,
                    )
                    Text(
                        formatTime(state.duration),
                        color = Color(0xFF8E8E93),
                        fontSize = 11.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- Transport: previous / play / next (Lark exact match: flat white icons, no circles) ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Previous button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(onClick = viewModel::skipPrevious),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_lark_prev),
                        contentDescription = "Anterior",
                        tint = Color(0xFFE6E6E6),
                        modifier = Modifier.size(46.dp),
                    )
                }

                // Play / Pause button (Lark iconic: flat double pill pause or single play triangle)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable(onClick = viewModel::togglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) DesignR.drawable.ic_lark_pause
                            else DesignR.drawable.ic_lark_play
                        ),
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        tint = Color(0xFFE6E6E6),
                        modifier = Modifier.size(62.dp),
                    )
                }

                // Next button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(onClick = viewModel::skipNext),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_lark_next),
                        contentDescription = "Siguiente",
                        tint = Color(0xFFE6E6E6),
                        modifier = Modifier.size(46.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // ---- EQ / Letras / queue (Lark Player original bottom row) ----
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(playerBackground)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: Equalizer / Tune sliders
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onEqualizer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_lark_equalizer),
                        contentDescription = "Ecualizador",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Center: "Letras" plain text
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onLyrics)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Letras",
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Right: Queue with mode indicator (ic_lark_queue / ic_lark_queue_shuffle)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onQueue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (currentMode == PlayerRepository.MODE_SHUFFLE) DesignR.drawable.ic_lark_queue_shuffle
                            else DesignR.drawable.ic_lark_queue
                        ),
                        contentDescription = "Cola de reproducción",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (visualizerSheetOpen) {
            MusicVisualizerSheet(
                enabled = state.visualizerEnabled,
                onEnabledChange = viewModel::setVisualizerEnabled,
                currentStyle = state.visualizerStyle,
                onStyleChange = viewModel::setVisualizerStyle,
                accent = accent,
                onDismissRequest = { visualizerSheetOpen = false },
            )
        }
    }

    // ---- Add to playlist ----
    if (playlistPickerOpen) {
        LarkSheet(onDismissRequest = { playlistPickerOpen = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp),
            ) {
                Text(
                    "Agregar a la lista de reproducción",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            playlistPickerOpen = false
                            newPlaylistName = ""
                            createPlaylistOpen = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Nueva playlist", color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.addCurrentToPlaylist(playlist.id)
                                    playlistPickerOpen = false
                                    Toast.makeText(context, "Agregada a ${playlist.name}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                null,
                                tint = Color(0xFFC9C9D0),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                playlist.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    Text(
                        "Todavía no tienes playlists",
                        color = ApplePalette.TextSecondary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (createPlaylistOpen) {
        LarkDialog(onDismissRequest = { createPlaylistOpen = false }) {
            Text("Nueva playlist", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            LarkTextField(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                placeholder = "Nombre de playlist",
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancelar",
                    color = ApplePalette.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            createPlaylistOpen = false
                            newPlaylistName = ""
                        }
                        .padding(12.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Crear",
                    color = if (newPlaylistName.isBlank()) Color(0xFF5A5A61) else ApplePalette.Blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylistWithCurrent(newPlaylistName.trim())
                            createPlaylistOpen = false
                            newPlaylistName = ""
                            Toast.makeText(context, "Playlist creada", Toast.LENGTH_SHORT).show()
                        }
                        .padding(12.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        LarkDialog(onDismissRequest = { confirmDelete = false }) {
            Text("Borrar canción", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "¿Borrar «${state.title}» del dispositivo? Esta acción no se puede deshacer.",
                color = ApplePalette.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancelar",
                    color = ApplePalette.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { confirmDelete = false }.padding(12.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Borrar",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            confirmDelete = false
                            requestDelete()
                        }
                        .padding(12.dp),
                )
            }
        }
    }
}

/** Small badge on the queue icon; tapping it cycles the playback mode. */
@Composable
private fun PlayerModeBadge(
    mode: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val icon = when (mode) {
        PlayerRepository.MODE_REPEAT_ONE -> Icons.Rounded.RepeatOne
        PlayerRepository.MODE_SHUFFLE -> Icons.Rounded.Shuffle
        else -> Icons.Rounded.Repeat
    }
    val description = when (mode) {
        PlayerRepository.MODE_REPEAT_ALL -> "Repetir todo"
        PlayerRepository.MODE_REPEAT_ONE -> "Repetir una"
        PlayerRepository.MODE_SHUFFLE -> "Aleatorio"
        else -> "Modo normal"
    }
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFF1B1C20))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (mode == PlayerRepository.MODE_NORMAL) Color.White.copy(alpha = 0.55f) else accent,
            modifier = Modifier.size(13.dp),
        )
    }
}

private fun nextMode(mode: Int): Int = when (mode) {
    PlayerRepository.MODE_REPEAT_ALL -> PlayerRepository.MODE_REPEAT_ONE
    PlayerRepository.MODE_REPEAT_ONE -> PlayerRepository.MODE_SHUFFLE
    else -> PlayerRepository.MODE_REPEAT_ALL
}

@Composable
private fun PlayerBottomLabel(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color(0xFFE2E2E8),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Transport button that springs when pressed, like a launcher icon. */
@Composable
private fun AnimatedTransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    tint: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        label = "transport_press",
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

private fun formatSpeed(speed: Float): String {
    val text = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "${text}x"
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}
