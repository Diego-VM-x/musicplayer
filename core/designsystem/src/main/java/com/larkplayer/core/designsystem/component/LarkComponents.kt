package com.larkplayer.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larkplayer.core.designsystem.theme.ApplePalette

/** Translucent glass card used by every Lark settings group. */
@Composable
fun LarkCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.11f),
                        Color.White.copy(alpha = 0.045f),
                        Color.White.copy(alpha = 0.02f),
                    )
                )
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape),
        content = content,
    )
}

/** Circular glass button (back arrow, header actions). */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String = "Atrás",
    tint: Color = Color.White,
    background: Color = Color(0xFF1D1D20),
) {
    GlassIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = 42.dp,
        iconSize = 21.dp,
        tint = tint,
    )
}

/**
 * A settings row: leading icon, bold title, optional subtitle, optional value
 * and a chevron. Matches the rows in Lark's Ajustes screen.
 */
@Composable
fun SettingsRow(
    icon: ImageVector?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
    iconTint: Color = Color(0xFFD6D6DC),
) {
    Row(
        modifier = modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .heightIn(min = 62.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = ApplePalette.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        if (value != null) {
            Text(value, color = Color(0xFF8E8E95), fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
        }
        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color(0xFF6E6E75),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
fun LarkDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 0.6.dp)
            .background(ApplePalette.Divider),
    )
}

@Composable
fun LarkSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = ApplePalette.TextSecondary,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Small selectable pill used for tabs and segmented controls. */
@Composable
fun LarkPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = ApplePalette.Blue,
    icon: ImageVector? = null,
    showText: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.White.copy(alpha = 0.07f))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.09f)),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (showText) 16.dp else 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFFB8B8BE),
                modifier = Modifier.size(16.dp),
            )
            if (showText) Spacer(Modifier.width(7.dp))
        }
        if (showText) {
            Text(
                text,
                color = if (selected) Color.White else Color(0xFFC5C5CB),
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun LarkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color = ApplePalette.Blue,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFF47474C),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Full-screen background with the top colour wash used by Lark. */
@Composable
fun LarkGradientBackground(
    accent: Color,
    modifier: Modifier = Modifier,
    strength: Float = 0.22f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = strength),
                    0.32f to accent.copy(alpha = strength * 0.35f),
                    0.62f to ApplePalette.Background,
                    1f to ApplePalette.Background,
                )
            ),
    ) { content() }
}

/** Thin, rounded progress line used by the player. */
@Composable
fun LarkProgressLine(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    track: Color = Color.White.copy(alpha = 0.22f),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(track)
            .heightIn(min = 3.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
        )
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .heightIn(min = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
    }
}
