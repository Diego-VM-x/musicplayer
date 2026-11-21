package com.larkplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.LarkSheet

/**
 * Full track options sheet exactly as seen in Lark Player original:
 * - Header: Album art, Title, Artist, Edit icon
 * - Items:
 *   - Agregar a la lista de reproducción
 *   - Compartir
 *   - Visualizador de música
 *   - Velocidad
 *   - Establecer como tono de llamada
 *   - Temporizador para dormir
 *   - Ocultar
 *   - Borrar
 */
@Composable
fun PlayerMoreSheet(
    title: String,
    artist: String,
    albumArt: String?,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onVisualizer: () -> Unit,
    onSpeed: () -> Unit,
    onSetRingtone: () -> Unit,
    onSleepTimer: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    LarkSheet(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            // Header: Song info
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2E)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = albumArt,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        artist.ifBlank { "Artista desconocido" },
                        color = Color(0xFF9E9EA6),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Editar",
                    tint = Color(0xFFC0C0C6),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            SheetOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                title = "Agregar a la lista de reproducción",
                onClick = onAddToPlaylist,
            )
            SheetOptionRow(
                icon = Icons.Rounded.Share,
                title = "Compartir",
                onClick = onShare,
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 10.dp),
            )

            SheetOptionRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Visualizador de música",
                onClick = onVisualizer,
            )
            SheetOptionRow(
                icon = Icons.Rounded.Speed,
                title = "Velocidad",
                onClick = onSpeed,
            )
            SheetOptionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Establecer como tono de llamada",
                onClick = onSetRingtone,
            )
            SheetOptionRow(
                icon = Icons.Rounded.Timer,
                title = "Temporizador para dormir",
                onClick = onSleepTimer,
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 10.dp),
            )

            SheetOptionRow(
                icon = Icons.Rounded.VisibilityOff,
                title = "Ocultar",
                onClick = onHide,
            )
            SheetOptionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Borrar",
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun SheetOptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFFE2E2E8),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
