package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkDivider
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.settings.SettingsViewModel

private data class Language(val label: String, val tag: String)

private val SUGGESTED = listOf(
    Language("Español", "es"),
    Language("English", "en"),
)

private val ALL = listOf(
    Language("Afrikaans", "af"),
    Language("Azərbaycanca", "az"),
    Language("Bahasa Indonesia", "id"),
    Language("Bahasa Melayu", "ms"),
    Language("Català", "ca"),
    Language("Dansk", "da"),
    Language("Deutsch", "de"),
    Language("Eesti", "et"),
    Language("English", "en"),
    Language("Español", "es"),
    Language("Euskara", "eu"),
    Language("Filipino", "fil"),
    Language("Français", "fr"),
    Language("Galego", "gl"),
    Language("Hrvatski", "hr"),
    Language("Italiano", "it"),
    Language("Kiswahili", "sw"),
    Language("Latviešu", "lv"),
    Language("Lietuvių", "lt"),
    Language("Magyar", "hu"),
    Language("Nederlands", "nl"),
    Language("Norsk", "no"),
    Language("Oʻzbekcha", "uz"),
    Language("Polski", "pl"),
    Language("Português", "pt"),
    Language("Română", "ro"),
    Language("Slovenčina", "sk"),
    Language("Slovenščina", "sl"),
    Language("Suomi", "fi"),
    Language("Svenska", "sv"),
    Language("Tiếng Việt", "vi"),
    Language("Türkçe", "tr"),
    Language("Ελληνικά", "el"),
    Language("Български", "bg"),
    Language("Русский", "ru"),
    Language("Українська", "uk"),
    Language("עברית", "he"),
    Language("العربية", "ar"),
    Language("فارسی", "fa"),
    Language("हिन्दी", "hi"),
    Language("ไทย", "th"),
    Language("한국어", "ko"),
    Language("日本語", "ja"),
    Language("简体中文", "zh"),
    Language("繁體中文", "zh-TW"),
)

/** Language picker with suggested + full lists, matching Lark's layout. */
@Composable
fun LanguageScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val current by viewModel.language.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(horizontal = 16.dp)) {
                    CircleIconButton(onClick = onBack)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Idioma",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    "Idiomas sugeridos",
                    color = ApplePalette.TextSecondary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(SUGGESTED) { language ->
                LanguageRow(language.label, language.tag == current, accent) {
                    viewModel.setLanguage(language.tag)
                }
            }
            item {
                LarkDivider(Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                Text(
                    "Todos los idiomas",
                    color = ApplePalette.TextSecondary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            items(ALL) { language ->
                LanguageRow(language.label, language.tag == current, accent) {
                    viewModel.setLanguage(language.tag)
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) accent else Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Rounded.Check, "Seleccionado", tint = accent, modifier = Modifier.height(19.dp))
        }
    }
}
