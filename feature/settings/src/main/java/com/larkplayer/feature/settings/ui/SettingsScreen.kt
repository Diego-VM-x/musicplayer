package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.model.ThemeConfig
import com.larkplayer.feature.settings.SettingsViewModel

/** Settings + personalization screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBackup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.themeConfig.collectAsStateWithLifecycle(ThemeConfig())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Text("APPEARANCE", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))

            ListItem(
                headlineContent = { Text("Dynamic colors") },
                supportingContent = { Text("Use wallpaper colors (Android 12+)") },
                trailingContent = {
                    Switch(config.useDynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
                },
            )
            ListItem(
                headlineContent = { Text("Dark theme") },
                supportingContent = { Text("System / Light / Dark") },
                trailingContent = {
                    Text(
                        when (config.themeMode) { 0 -> "Light"; 1 -> "Dark"; else -> "System" },
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )

            Text("PLAYBACK", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            ListItem(headlineContent = { Text("Queue, shuffle, repeat, gapless — see Player") })

            Text("DATA", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            ListItem(
                headlineContent = { Text("Backup & Restore") },
                supportingContent = { Text("Export or import a ZIP backup") },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.TextButton(
                onClick = onBackup,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Open backup") }
        }
    }
}
