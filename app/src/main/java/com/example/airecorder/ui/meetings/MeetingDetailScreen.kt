package com.example.airecorder.ui.meetings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airecorder.domain.model.TranscriptStatus
import com.example.airecorder.ui.components.SectionCard
import com.example.airecorder.util.formatDateTime
import com.example.airecorder.util.formatDuration
import java.io.File

@Composable
fun MeetingDetailScreen(
    uiState: MeetingDetailUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onShareRecording: () -> Unit,
    onGenerateTranscript: () -> Unit,
    onTranslateTranscript: (String) -> Unit,
    onConsumeShareAudioPath: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val detail = uiState.detail ?: return
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember(detail.meeting.name) { mutableStateOf(detail.meeting.name) }
    var showTranscriptDialog by remember { mutableStateOf(false) }
    var showTranslationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var translateLanguageInput by remember(uiState.translationTargetLanguage) {
        mutableStateOf(uiState.translationTargetLanguage)
    }
    val duration = uiState.playbackDurationMs.takeIf { it > 0 } ?: detail.meeting.durationMs

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessage()
        }
    }

    LaunchedEffect(uiState.shareAudioPath) {
        uiState.shareAudioPath?.let {
            shareRecording(context, it)
            onConsumeShareAudioPath()
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
                        detail.meeting.createdAt.formatDateTime(),
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

        SectionCard("Playback") {
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
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionGlyph(
                        icon = Icons.Outlined.Replay,
                        onClick = { onSeek(0L) },
                        contentDescription = "Repeat",
                    )
                    IconButton(
                        onClick = onPlayPause,
                        enabled = !uiState.isResolvingAudio,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF2F80FF), CircleShape),
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                        )
                    }
                    ActionGlyph(
                        icon = Icons.Outlined.Share,
                        onClick = onShareRecording,
                        contentDescription = "Share recording",
                    )
                }
            }
        }

        SectionCard("Actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    label = "Transcript",
                    enabled = detail.meeting.transcriptStatus != TranscriptStatus.PROCESSING,
                    onClick = onGenerateTranscript,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    label = "Translate",
                    enabled = detail.meeting.transcriptStatus == TranscriptStatus.COMPLETED && !uiState.isTranslating,
                    onClick = {
                        translateLanguageInput = uiState.translationTargetLanguage
                        showLanguageDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            ActionStatusRow(
                title = "Transcript",
                isProcessing = detail.meeting.transcriptStatus == TranscriptStatus.PROCESSING,
                isReady = detail.meeting.transcriptStatus == TranscriptStatus.COMPLETED &&
                    uiState.transcriptDraft.isNotBlank(),
                isFailed = detail.meeting.transcriptStatus == TranscriptStatus.FAILED,
                readyText = "View transcript",
                onView = { showTranscriptDialog = true },
            )

            ActionStatusRow(
                title = "Translation",
                isProcessing = uiState.isTranslating,
                isReady = uiState.translatedTranscript.isNotBlank(),
                isFailed = false,
                readyText = "View translation",
                onView = { showTranslationDialog = true },
            )
        }

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
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Recording name") },
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
            title = { Text("Delete Recording") },
            text = { Text("This will delete the recording, transcript, and translation.") },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onTranslateTranscript(translateLanguageInput)
                    showLanguageDialog = false
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") } },
            title = { Text("Translate transcript") },
            text = {
                OutlinedTextField(
                    value = translateLanguageInput,
                    onValueChange = { translateLanguageInput = it },
                    label = { Text("Target language") },
                    supportingText = { Text("Use a language code like es, fr, de, zh.") },
                )
            },
        )
    }

    if (showTranscriptDialog) {
        DocumentDialog(
            title = "Transcript",
            text = uiState.transcriptDraft,
            onDismiss = { showTranscriptDialog = false },
        )
    }

    if (showTranslationDialog) {
        DocumentDialog(
            title = "Translation (${uiState.translationTargetLanguage.uppercase()})",
            text = uiState.translatedTranscript,
            onDismiss = { showTranslationDialog = false },
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 6.dp,
            end = 8.dp,
            bottom = 6.dp,
        ),
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ActionGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color(0xFFF1F5F9), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = contentDescription, tint = Color(0xFF65748B))
        }
    }
}

@Composable
private fun ActionStatusRow(
    title: String,
    isProcessing: Boolean,
    isReady: Boolean,
    isFailed: Boolean,
    readyText: String,
    onView: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        when {
            isProcessing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 2.dp), strokeWidth = 2.dp)
                    Text("In progress", color = Color(0xFF2563EB))
                }
            }

            isReady -> {
                TextButton(onClick = onView) {
                    Text(readyText)
                }
            }

            isFailed -> {
                Text("Failed", color = Color(0xFFDC2626))
            }

            else -> {
                Text("Not started", color = Color(0xFF7B8598))
            }
        }
    }
}

@Composable
private fun DocumentDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text(title) },
        text = {
            Text(
                text = text.ifBlank { "No content available." },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

private fun shareRecording(context: Context, audioFilePath: String) {
    val file = File(audioFilePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share recording",
        ),
    )
}
