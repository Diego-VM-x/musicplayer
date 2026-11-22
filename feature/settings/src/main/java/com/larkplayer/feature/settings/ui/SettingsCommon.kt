package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkSwitch
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.designsystem.theme.ApplePalette.Blue

/** Standard Lark settings page: circular back, large title, scrollable body. */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        CircleIconButton(onClick = onBack)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            lineHeight = 31.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 22.dp),
        )
        content()
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Icon + title + switch row used by "Ajustes de reproducción".
 * The subtitle is rendered under the title, aligned with it.
 */
@Composable
fun PlayToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    accent: Color = Blue,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = Color(0xFFD6D6DC), modifier = Modifier.padding(top = 2.dp, end = 16.dp).height(26.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    color = Color(0xFF7E7E86),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(0.dp))
        LarkSwitch(checked = checked, onCheckedChange = onCheckedChange, accent = accent)
    }
}

/** Equal-width segmented selector with a blue active pill. */
@Composable
fun LarkSegmented(
    options: List<Triple<String, ImageVector, Boolean>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Blue,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1B1E), RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, (label, icon, selected) ->
            Row(
                Modifier
                    .weight(1f)
                    .background(
                        if (selected) accent else Color.Transparent,
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (selected) Color.Black else Color(0xFFB9B9BF),
                    modifier = Modifier.height(15.dp),
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    "  $label",
                    color = if (selected) Color.Black else Color(0xFFC5C5CB),
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}
