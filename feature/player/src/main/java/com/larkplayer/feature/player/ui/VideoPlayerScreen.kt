package com.larkplayer.feature.player.ui

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.theme.ApplePalette

/**
 * Video surface for local files. It reuses the SAME MediaController/ExoPlayer
 * as the audio player, so playback survives rotation and backgrounding; no
 * second player and no extra permission are created here.
 */
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val controller by viewModel.controller.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // PlayerView follows the controller only while this screen is composed.
    DisposableEffect(Unit) { onDispose { /* the controller is owned by PlayerRepository */ } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(event: MotionEvent): Boolean {
                            val player = player ?: return false
                            val delta = VideoGestures.seekDelta(
                                tapX = event.x,
                                viewWidth = width,
                                positionMs = player.currentPosition,
                                durationMs = player.duration,
                            )
                            if (delta != 0L) player.seekTo(player.currentPosition + delta)
                            return true
                        }
                    })
                    // Return false so PlayerView keeps its own tap/seek/pinch gestures.
                    setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
                }
            },
            update = { view -> view.player = controller },
            modifier = Modifier.fillMaxSize(),
        )

        CircleIconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        if (state.title.isNotBlank()) {
            Text(
                state.title,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
            )
        }
        Text(
            "Toca para controles · doble toque para avanzar/retroceder 10 s",
            color = ApplePalette.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}
