package com.example.airecorder.ui.settings

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.airecorder.domain.model.SummaryType
import com.example.airecorder.ui.components.SectionCard
import com.example.airecorder.util.formatBytes

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    uiState: SettingsUiState,
    onAutoTranscribeChanged: (Boolean) -> Unit,
    onAutoSummaryChanged: (Boolean) -> Unit,
    onSummaryTypeChanged: (SummaryType) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var showSummaryPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
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

        SectionCard("Storage") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StorageRing(
                    totalLabel = uiState.storageStats.totalBytes.formatBytes(),
                    usedLabel = "of 10 GB used",
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLegend("Audio", uiState.storageStats.audioBytes.formatBytes(), Color(0xFF2F80FF))
                    StorageLegend("Transcripts", (uiState.storageStats.textBytes / 2).formatBytes(), Color(0xFFFF9F43))
                    StorageLegend("Summaries", (uiState.storageStats.textBytes / 2).formatBytes(), Color(0xFF2ED573))
                    StorageLegend("Other", "0 B", Color(0xFFD1D5DB))
                    Text("Manage storage >", color = Color(0xFF2F80FF), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionCard("Preferences") {
            PreferenceToggle(
                title = "Auto-transcribe after save",
                subtitle = "Generate transcript automatically",
                checked = uiState.preferences.autoTranscribe,
                onCheckedChange = onAutoTranscribeChanged,
            )
            PreferenceToggle(
                title = "Auto-summary after transcript",
                subtitle = "Generate summary automatically",
                checked = uiState.preferences.autoSummary,
                onCheckedChange = onAutoSummaryChanged,
            )
            PreferenceRow(
                icon = Icons.Outlined.Tune,
                title = "Summary style",
                value = uiState.preferences.summaryType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                onClick = { showSummaryPicker = true },
            )
            PreferenceRow(
                icon = Icons.Outlined.Translate,
                title = "Transcription language",
                value = languages.firstOrNull { it.second == uiState.preferences.transcriptionLanguage }?.first ?: uiState.preferences.transcriptionLanguage,
                onClick = { showLanguagePicker = true },
            )
        }

        SectionCard("Privacy") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFFFF3E9), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFFB923C))
                }
                Column {
                    Text("All data stays on your device.", fontWeight = FontWeight.SemiBold)
                    Text("No upload and no sharing of your recordings.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7B8598))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFEBEE), RoundedCornerShape(18.dp))
                .clickable { confirmDelete = true }
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444))
                Text("Delete All Recordings & Data", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Delete All Recordings & Data") },
            text = { Text("This will delete every local recording, transcript, and summary.") },
        )
    }

    if (showSummaryPicker) {
        PickerDialog(
            title = "Summary style",
            options = SummaryType.entries.map { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } },
            selected = uiState.preferences.summaryType.ordinal,
            onDismiss = { showSummaryPicker = false },
            onSelect = {
                onSummaryTypeChanged(SummaryType.entries[it])
                showSummaryPicker = false
            },
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
}

@Composable
private fun StorageRing(totalLabel: String, usedLabel: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0xFFE5E7EB), -90f, 360f, false, style = stroke, size = Size(size.width, size.height))
            drawArc(Color(0xFF2F80FF), -90f, 180f, false, style = stroke, size = Size(size.width, size.height))
            drawArc(Color(0xFFFF9F43), 90f, 72f, false, style = stroke, size = Size(size.width, size.height))
            drawArc(Color(0xFF2ED573), 162f, 54f, false, style = stroke, size = Size(size.width, size.height))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(totalLabel, fontWeight = FontWeight.Bold)
            Text(usedLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7B8598))
        }
    }
}

@Composable
private fun StorageLegend(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xFF7B8598), style = MaterialTheme.typography.bodySmall)
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
