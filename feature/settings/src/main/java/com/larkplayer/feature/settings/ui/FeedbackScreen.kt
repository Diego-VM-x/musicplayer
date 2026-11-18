package com.larkplayer.feature.settings.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larkplayer.core.designsystem.theme.ApplePalette

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }

    SettingsPage(title = "Opinión", onBack = onBack) {
        Text(
            "Cuéntanos qué podemos mejorar. Tu mensaje se abrirá en tu app de correo; no se envía nada automáticamente.",
            color = ApplePalette.TextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Tu mensaje") },
            minLines = 5,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(ApplePalette.Blue, RoundedCornerShape(50))
                .clickable {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:dev@larkclean.local")
                        putExtra(Intent.EXTRA_SUBJECT, "Opinión Lark Player mod")
                        putExtra(Intent.EXTRA_TEXT, message.ifBlank { "Hola, me gustaría sugerir..." })
                    }
                    runCatching { context.startActivity(intent) }
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Send, null, tint = Color.Black, modifier = Modifier.height(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enviar opinión", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MailOutline, null, tint = Color(0xFF8E8E95), modifier = Modifier.height(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("dev@larkclean.local", color = ApplePalette.TextSecondary, fontSize = 12.sp)
        }
    }
}
