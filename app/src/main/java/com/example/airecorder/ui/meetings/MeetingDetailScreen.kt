package com.example.airecorder.ui.meetings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.airecorder.ui.components.LabeledValue
import com.example.airecorder.ui.components.SectionCard
import com.example.airecorder.util.formatBytes
import com.example.airecorder.util.formatDateTime
import com.example.airecorder.util.formatDuration

@Composable
fun MeetingDetailScreen(
    uiState: MeetingDetailUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onGenerateTranscript: () -> Unit,
    onTranslateTranscript: () -> Unit,
    onGenerateSummary: () -> Unit,
    onTranscriptTextChange: (String) -> Unit,
    onSummaryTextChange: (String) -> Unit,
    onSaveTranscript: () -> Unit,
    onSaveSummary: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val detail = uiState.detail ?: return
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember(detail.meeting.name) { mutableStateOf(detail.meeting.name) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val duration = uiState.playbackDurationMs.takeIf { it > 0 } ?: detail.meeting.durationMs

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(detail.meeting.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${detail.meeting.createdAt.formatDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7B8598),
                    )
                }
            }
            Row {
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
        }

        SectionCard(title = "Playback") {
            WaveStrip()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(uiState.currentPositionMs.formatDuration(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF7B8598))
                Text(duration.formatDuration(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF7B8598))
            }
            Slider(
                value = uiState.currentPositionMs.coerceAtMost(duration).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFF1F5F9), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("1x", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF2F80FF), CircleShape),
                ) {
                    Icon(
                        if (uiState.isPlaying) Icons.Outlined.Refresh else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionGlyph(Icons.Outlined.Share)
                    ActionGlyph(Icons.Outlined.Edit)
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Transcript", "Summary").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    label = { Text(label) },
                )
            }
        }

        if (selectedTab == 0) {
            SectionCard("Transcript") {
                OutlinedTextField(
                    value = uiState.transcriptDraft,
                    onValueChange = onTranscriptTextChange,
                    placeholder = { Text("Search transcript...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(uiState.transcriptDraft.ifBlank { "Transcript will appear here once generated." })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onGenerateTranscript) { Text(if (detail.transcript == null) "Generate" else "Retry") }
                    Button(
                        onClick = onTranslateTranscript,
                        enabled = uiState.transcriptDraft.isNotBlank() && !uiState.isTranslating,
                    ) {
                        Text(if (uiState.isTranslating) "Translating..." else "Translate")
                    }
                    Button(onClick = onSaveTranscript) { Text("Save") }
                    TextButton(onClick = { shareText(context, uiState.transcriptDraft) }) { Text("Share") }
                }
                if (uiState.translatedTranscript.isNotBlank()) {
                    SectionCard("Translated (${uiState.translationTargetLanguage.uppercase()})") {
                        Text(uiState.translatedTranscript)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { shareText(context, uiState.translatedTranscript) }) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Share")
                            }
                            TextButton(onClick = onTranslateTranscript, enabled = !uiState.isTranslating) {
                                Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Retranslate")
                            }
                        }
                    }
                }
            }
        } else {
            SectionCard("Summary") {
                Text("Summary style: ${uiState.summaryType.name.replace('_', ' ')}", color = Color(0xFF7B8598))
                OutlinedTextField(
                    value = uiState.summaryDraft,
                    onValueChange = onSummaryTextChange,
                    placeholder = { Text("Summary will appear here.") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onGenerateSummary) { Text(if (detail.summary == null) "Generate" else "Regenerate") }
                    Button(onClick = onSaveSummary) { Text("Save") }
                    TextButton(onClick = { shareText(context, uiState.summaryDraft) }) { Text("Share") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LabeledValue("Created", detail.meeting.createdAt.formatDateTime(), Modifier.weight(1f))
            LabeledValue("Duration", detail.meeting.durationMs.formatDuration(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LabeledValue("File size", detail.meeting.fileSizeBytes.formatBytes(), Modifier.weight(1f))
            LabeledValue(
                "Source",
                if (detail.meeting.recordingMode == com.example.airecorder.domain.model.RecordingMode.MIC) "Mic" else "Playback",
                Modifier.weight(1f),
            )
        }
        LabeledValue("Capture notes", detail.meeting.captureNotes)

        SnackbarHost(hostState = snackbarHostState)
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onRename(renameText)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
            title = { Text("Rename Meeting") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Meeting name") },
                )
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
            title = { Text("Delete Meeting") },
            text = { Text("This will delete the recording, transcript, and summary. This action cannot be undone.") },
        )
    }
}

@Composable
private fun WaveStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(8, 14, 10, 18, 12, 15, 7, 11, 17, 9, 13, 8, 16, 10).forEach { height ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height.dp)
                    .background(Color(0xFFC8D4EA), RoundedCornerShape(100)),
            )
        }
    }
}

@Composable
private fun ActionGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color(0xFFF1F5F9), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF65748B), modifier = Modifier.size(18.dp))
    }
}

private fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share",
        ),
    )
}
