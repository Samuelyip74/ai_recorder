package com.example.airecorder.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.airecorder.ui.components.SectionCard

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    uiState: SettingsUiState,
    onAutoTranscribeChanged: (Boolean) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onTranslationTargetLanguageChanged: (String) -> Unit,
    onLogout: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showTranslationTargetPicker by remember { mutableStateOf(false) }
    val languages = listOf("English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de")

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionCard("Preferences") {
            PreferenceToggle(
                title = "Auto-transcribe after save",
                subtitle = "Generate transcript automatically",
                checked = uiState.preferences.autoTranscribe,
                onCheckedChange = onAutoTranscribeChanged,
            )
            PreferenceRow(
                icon = Icons.Outlined.Translate,
                title = "Transcription language",
                value = languages.firstOrNull { it.second == uiState.preferences.transcriptionLanguage }?.first ?: uiState.preferences.transcriptionLanguage,
                onClick = { showLanguagePicker = true },
            )
            PreferenceRow(
                icon = Icons.Outlined.Translate,
                title = "Translation target",
                value = languages.firstOrNull { it.second == uiState.preferences.translationTargetLanguage }?.first
                    ?: uiState.preferences.translationTargetLanguage,
                onClick = { showTranslationTargetPicker = true },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFEAF1FF), RoundedCornerShape(18.dp))
                .clickable { confirmLogout = true }
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Color(0xFF1F67E7))
                Text("Logout", color = Color(0xFF1F67E7), fontWeight = FontWeight.SemiBold)
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            confirmButton = {
                TextButton(onClick = {
                    onLogout()
                    confirmLogout = false
                }) { Text("Logout") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
            title = { Text("Logout") },
            text = { Text("This will sign you out of RB-Notes on this device.") },
        )
    }

    if (showLanguagePicker) {
        PickerDialog(
            title = "Transcription language",
            options = languages.map { it.first },
            selected = languages.indexOfFirst { it.second == uiState.preferences.transcriptionLanguage }.coerceAtLeast(0),
            onDismiss = { showLanguagePicker = false },
            onSelect = {
                onLanguageChanged(languages[it].second)
                showLanguagePicker = false
            },
        )
    }

    if (showTranslationTargetPicker) {
        PickerDialog(
            title = "Translation target language",
            options = languages.map { it.first },
            selected = languages.indexOfFirst { it.second == uiState.preferences.translationTargetLanguage }.coerceAtLeast(0),
            onDismiss = { showTranslationTargetPicker = false },
            onSelect = {
                onTranslationTargetLanguageChanged(languages[it].second)
                showTranslationTargetPicker = false
            },
        )
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7B8598))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreferenceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF65748B))
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xFF65748B), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == selected) Color(0xFFEAF1FF) else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(index) }
                            .padding(12.dp),
                    ) {
                        Text(option)
                    }
                }
            }
        },
    )
}
