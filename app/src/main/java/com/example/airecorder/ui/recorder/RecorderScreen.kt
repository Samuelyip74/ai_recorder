package com.example.airecorder.ui.recorder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.airecorder.domain.model.RecorderState
import com.example.airecorder.util.formatDuration

@Composable
fun RecorderScreen(
    paddingValues: PaddingValues,
    uiState: RecorderUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismissDraft: () -> Unit,
    onConfirmSave: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var meetingName by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) onStart() else onPermissionDenied() },
    )

    LaunchedEffect(uiState.recorderState) {
        if (uiState.recorderState != RecorderState.STOPPED_AWAITING_NAME) {
            meetingName = ""
        }
    }

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
            .padding(paddingValues),
    ) {
        RecorderHero()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFEEF0), RoundedCornerShape(100))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.FiberManualRecord,
                                contentDescription = null,
                                tint = if (uiState.recorderState == RecorderState.RECORDING) Color(0xFFEF4444) else Color(0xFF9CA3AF),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = when (uiState.recorderState) {
                                    RecorderState.RECORDING -> "Recording"
                                    RecorderState.PAUSED -> "Paused"
                                    RecorderState.SAVING -> "Saving"
                                    else -> "Ready"
                                },
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Text(
                    text = uiState.elapsedMs.formatDuration(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF20263A),
                )
                Text("Recording time", color = Color(0xFF8A94A6))

                RecorderWaveform(active = uiState.recorderState == RecorderState.RECORDING)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecorderControl(
                        label = if (uiState.recorderState == RecorderState.PAUSED) "Resume" else "Pause",
                        icon = if (uiState.recorderState == RecorderState.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        background = Color(0xFFEAF1FF),
                        enabled = uiState.recorderState == RecorderState.RECORDING || uiState.recorderState == RecorderState.PAUSED,
                        onClick = {
                            if (uiState.recorderState == RecorderState.PAUSED) onResume() else onPause()
                        },
                    )
                    RecorderControl(
                        label = "Stop",
                        icon = Icons.Outlined.Stop,
                        background = Color(0xFFFF4D4F),
                        iconTint = Color.White,
                        enabled = uiState.recorderState == RecorderState.RECORDING || uiState.recorderState == RecorderState.PAUSED,
                        onClick = onStop,
                        large = true,
                    )
                    RecorderControl(
                        label = "Start",
                        icon = Icons.Outlined.PlayArrow,
                        background = Color(0xFFEAF1FF),
                        enabled = uiState.recorderState == RecorderState.IDLE || uiState.recorderState == RecorderState.ERROR,
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) onStart() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEFF5FF), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        "Tip: you can pause and resume recording anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4A79D9),
                    )
                }

                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }

    if (uiState.recorderState == RecorderState.STOPPED_AWAITING_NAME && uiState.pendingDraft != null) {
        AlertDialog(
            onDismissRequest = onDismissDraft,
            confirmButton = { TextButton(onClick = { onConfirmSave(meetingName) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismissDraft) { Text("Cancel") } },
            title = { Text("Save Meeting") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter a name for your recording")
                    OutlinedTextField(
                        value = meetingName,
                        onValueChange = { meetingName = it },
                        placeholder = { Text("e.g. Team Stand-up") },
                        supportingText = { Text("${meetingName.length}/60") },
                    )
                }
            },
        )
    }
}

@Composable
private fun RecorderHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2F80FF), Color(0xFF1F67E7))),
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Color.White)
                    Text("AI Recorder", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Record, transcribe, summarize.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RecorderWaveform(active: Boolean) {
    val bars = listOf(12, 28, 20, 42, 26, 34, 18, 40, 22, 36, 16, 30, 44, 24, 38, 20)
    Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
        val spacing = size.width / (bars.size * 1.4f)
        bars.forEachIndexed { index, bar ->
            val x = index * spacing * 1.4f
            val height = bar.dp.toPx() * if (active) 1f else 0.6f
            drawRoundRect(
                color = if (active) Color(0xFF2F80FF) else Color(0xFFBFD4FF),
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height / 2 - height / 2),
                size = androidx.compose.ui.geometry.Size(spacing, height),
                cornerRadius = CornerRadius(spacing, spacing),
            )
        }
    }
}

@Composable
private fun RecorderControl(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    large: Boolean = false,
    iconTint: Color = Color(0xFF405067),
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(if (large) 74.dp else 60.dp)
                .background(background.copy(alpha = if (enabled) 1f else 0.45f), CircleShape),
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(if (large) 34.dp else 28.dp))
        }
        Text(label, color = Color(0xFF7B8598), style = MaterialTheme.typography.bodySmall)
    }
}
