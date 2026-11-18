package com.larkplayer.feature.library.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassPillButton
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Simple in-app square cropper for playlist covers: pinch to zoom, drag to
 * reposition, then saves a 512×512 JPEG to app-private storage.
 */
@Composable
fun CoverCropper(
    sourceUri: String,
    accent: Color,
    onCancel: () -> Unit,
    onApply: (String) -> Unit,
) {
    val context = LocalContext.current
    val side = 320.dp
    val sidePx = with(LocalDensity.current) { side.toPx() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(sourceUri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(sourceUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                }
            }.getOrNull()
        }
    }

    LarkGradientBackground(accent = accent, strength = 0.24f) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Rounded.Close, "Cancelar", onClick = onCancel)
                Spacer(Modifier.weight(1f))
                Text("Recortar portada", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }

            val bmp = bitmap
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp == null) {
                    Text("No se pudo abrir la imagen", color = ApplePalette.TextSecondary, fontSize = 13.sp)
                } else {
                    val baseScale = max(sidePx / bmp.width, sidePx / bmp.height)
                    val shownW = bmp.width * baseScale * scale
                    val shownH = bmp.height * baseScale * scale
                    val maxX = max(0f, (shownW - sidePx) / 2f)
                    val maxY = max(0f, (shownH - sidePx) / 2f)
                    offset = Offset(
                        offset.x.coerceIn(-maxX, maxX),
                        offset.y.coerceIn(-maxY, maxY),
                    )

                    Box(
                        Modifier
                            .size(side)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF222226))
                            .pointerInput(bmp) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = Offset(offset.x + pan.x, offset.y + pan.y)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Portada a recortar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                        )
                    }
                }
            }

            Text(
                "Pellizca para acercar y arrastra para recolocar",
                color = ApplePalette.TextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassPillButton("Cancelar", onClick = onCancel, modifier = Modifier.weight(1f))
                GlassPillButton(
                    "Aplicar",
                    onClick = {
                        val source = bitmap
                        if (source == null) {
                            Toast.makeText(context, "No hay imagen para recortar", Toast.LENGTH_SHORT).show()
                        } else {
                            val result = cropSquare(context, source, scale, offset, sidePx)
                            if (result != null) onApply(result) else {
                                Toast.makeText(context, "No se pudo recortar la imagen", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    filled = true,
                )
            }
        }
    }
}

/** Crops the visible square and stores it in app-private storage. */
private fun cropSquare(
    context: Context,
    source: Bitmap,
    scale: Float,
    offset: Offset,
    sidePx: Float,
): String? = runCatching {
    val effective = sidePx / (scale * max(sidePx / source.width, sidePx / source.height))
    val centerX = source.width / 2f - offset.x / (scale * max(sidePx / source.width, sidePx / source.height))
    val centerY = source.height / 2f - offset.y / (scale * max(sidePx / source.width, sidePx / source.height))
    val left = (centerX - effective / 2f).roundToInt().coerceIn(0, source.width - 1)
    val top = (centerY - effective / 2f).roundToInt().coerceIn(0, source.height - 1)
    val size = effective.roundToInt().coerceAtMost(minOf(source.width - left, source.height - top))
    val cropped = Bitmap.createBitmap(source, left, top, max(1, size), max(1, size))
    val squared = Bitmap.createScaledBitmap(cropped, 512, 512, true)
    val dir = File(context.filesDir, "covers").apply { mkdirs() }
    val file = File(dir, "cover_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { squared.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    Uri.fromFile(file).toString()
}.getOrNull()
