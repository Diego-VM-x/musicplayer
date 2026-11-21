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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.larkplayer.core.designsystem.theme.ApplePalette

/*
 * Modern Velvet & Silk design primitives.
 *
 * Clean, tactile, and non-flat aesthetic with rich charcoal surfaces,
 * subtle satin light borders, soft ambient depth, and responsive fluid motion.
 */

private val GlassFill: Brush
    get() = Brush.verticalGradient(
        listOf(
            Color(0xFF26272C),
            Color(0xFF1C1D21),
        )
    )

private val GlassStroke: Brush
    get() = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.16f),
            Color.White.copy(alpha = 0.04f),
        )
    )

/** Solid, refined charcoal sheet background. */
val GlassSheetColor = Color(0xFF1A1B1F)

/**
 * Floating circular tactile button with ambient depth.
 *
 * [flat] renders a bare icon with ripple, matching Lark's player controls;
 * the circular surface is kept for floating actions such as play/pause.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Color.White,
    containerAlpha: Float = 0.10f,
    flat: Boolean = false,
) {
    val isFlat = LocalFlatSurface.current
    val base = modifier.size(size)
    Box(
        modifier = when {
            flat -> base.clip(CircleShape).clickable(onClick = onClick)
            isFlat -> base
                .clip(CircleShape)
                .background(Color(0xFF1D1E23))
                .clickable(onClick = onClick)
            else -> base
                .shadow(8.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF2E2F35),
                            Color(0xFF202126),
                        )
                    )
                )
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
                .clickable(onClick = onClick)
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Floating glass pill that groups several icon actions (sort + overflow). */
@Composable
fun GlassIconCluster(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val isFlat = LocalFlatSurface.current
    Row(
        modifier = modifier
            .then(
                if (isFlat) Modifier
                else Modifier.shadow(12.dp, RoundedCornerShape(50), clip = false)
            )
            .clip(RoundedCornerShape(50))
            .background(
                if (isFlat) Brush.linearGradient(listOf(Color(0xFF16171A), Color(0xFF16171A)))
                else GlassFill
            )
            .then(
                if (isFlat) Modifier
                else Modifier.border(BorderStroke(1.dp, GlassStroke), RoundedCornerShape(50))
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun GlassClusterIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    dividerBefore: Boolean = false,
) {
    if (dividerBefore) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .width(1.dp)
                .height(20.dp)
                .background(Color.White.copy(alpha = 0.10f))
        )
    }
    IconButton(onClick = onClick, modifier = modifier.size(38.dp)) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/** Large glass pill button ("Reproducir", "Aleatorio"). */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = ApplePalette.Blue,
    filled: Boolean = false,
) {
    val isFlat = LocalFlatSurface.current
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    isFlat && filled -> Brush.linearGradient(listOf(accent, accent))
                    isFlat -> Brush.linearGradient(listOf(Color(0xFF24252B), Color(0xFF24252B)))
                    filled -> Brush.linearGradient(listOf(accent, accent))
                    else -> GlassFill
                }
            )
            .then(
                if (isFlat) Modifier
                else Modifier.border(
                    BorderStroke(
                        1.dp,
                        if (filled) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        else GlassStroke,
                    ),
                    RoundedCornerShape(50),
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (filled) Color.White else accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            color = if (filled) Color.White else accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Rounded glass surface used for inline chips / groups. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val isFlat = LocalFlatSurface.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isFlat) Color(0xFF1D1E23) else Color(0xFF25262B))
            .then(
                if (isFlat) Modifier
                else Modifier.border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    RoundedCornerShape(50),
                )
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Bottom sheet with the Lark glass treatment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LarkSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = GlassSheetColor,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
            )
        },
        content = content,
    )
}

/** Floating glass menu (dropdown replacement). */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xF21A1A1E),
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        content = content,
    )
}

@Composable
fun GlassMenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val color = when {
        destructive -> Color(0xFFFF6B6B)
        selected -> ApplePalette.Blue
        else -> Color(0xFFE8E8EC)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            label,
            color = color,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Full-width glass dialog card. */
@Composable
fun LarkDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val isFlat = LocalFlatSurface.current
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .then(
                    if (isFlat) Modifier
                    else Modifier.shadow(24.dp, RoundedCornerShape(26.dp), clip = false)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(if (isFlat) Color(0xFF16171A) else Color(0xFF1E1F24))
                .then(
                    if (isFlat) Modifier
                    else Modifier.border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(26.dp),
                    )
                )
                .padding(24.dp),
            content = content,
        )
    }
}

/** Circular selection control used by multi-select lists. */
@Composable
fun SoftCheck(
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = ApplePalette.Blue,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(accent)
                else Modifier.border(1.6.dp, Color(0xFF6E6E76), CircleShape)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/** Floating bottom action bar shown while selecting songs. */
@Composable
fun GlassSelectionBar(
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
) {
    val isFlat = LocalFlatSurface.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .then(
                if (isFlat) Modifier
                else Modifier.shadow(24.dp, RoundedCornerShape(26.dp), clip = false)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(if (isFlat) Color(0xFF16171A) else Color(0xF21A1A1E))
            .then(
                if (isFlat) Modifier
                else Modifier.border(BorderStroke(1.dp, GlassStroke), RoundedCornerShape(26.dp))
            )
            .padding(vertical = 10.dp, horizontal = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                val tint = if (action.destructive) Color(0xFFFF6B6B) else ApplePalette.Blue
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = action.onClick)
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(action.icon, action.label, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        action.label,
                        color = if (action.destructive) Color(0xFFFF8A8A) else Color(0xFFAEAEB6),
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Huge, editable title used by the playlist editor. */
@Composable
fun LarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 200,
    accent: Color = ApplePalette.Blue,
) {
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(accent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color(0xFF7C7C84), fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(alpha = 0.85f)))
        Text(
            "${value.length} / $maxLength",
            color = Color(0xFF7C7C84),
            fontSize = 10.5.sp,
            modifier = Modifier.align(Alignment.End).padding(top = 6.dp),
        )
    }
}

/** Soft hairline separator. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 0.7.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}

/** Small glass tag (LETRAS, counts…). */
@Composable
fun GlassTag(text: String, modifier: Modifier = Modifier) {
    val isFlat = LocalFlatSurface.current
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (isFlat) Color(0xFF2A2B31) else Color.White.copy(alpha = 0.07f))
            .then(
                if (isFlat) Modifier
                else Modifier.border(
                    BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
                    RoundedCornerShape(5.dp),
                )
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text, color = Color(0xFFC9C9D0), fontSize = 7.sp, letterSpacing = 0.4.sp)
    }
}
