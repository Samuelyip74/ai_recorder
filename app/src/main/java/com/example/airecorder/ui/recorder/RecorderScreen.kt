package com.example.airecorder.ui.recorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.LayoutDirection
import com.example.airecorder.domain.model.RecordingMode
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
    onModeSelected: (RecordingMode) -> Unit,
    onBeginPlaybackConsentRequest: () -> Long,
    onPlaybackCaptureGranted: (Int, android.content.Intent?) -> Unit,
    onPermissionDenied: () -> Unit,
    onProjectionConsentDenied: () -> Unit,
    onProjectionLaunchUnavailable: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val tag = "RecorderScreen"
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var meetingName by remember { mutableStateOf("") }
    val projectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                onPlaybackCaptureGranted(result.resultCode, result.data)
            } else {
                onProjectionConsentDenied()
            }
        },
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (!granted) {
                onPermissionDenied()
            } else {
                when (uiState.selectedMode) {
                    RecordingMode.MIC -> onStart()
                    RecordingMode.PLAYBACK_CAPTURE -> {
                        if (!uiState.support.isSupported) {
                            Log.d(tag, "Playback unsupported: ${uiState.support.message}")
                            onProjectionConsentDenied()
                        } else {
                            val manager = projectionManager
                            if (manager != null) {
                                val launchId = onBeginPlaybackConsentRequest()
                                Log.d(tag, "Launching MediaProjection consent from permission callback launchId=$launchId.")
                                projectionLauncher.launch(createProjectionIntent(manager))
                            } else {
                                Log.e(tag, "MediaProjectionManager is null in permission callback.")
                                onProjectionLaunchUnavailable()
                            }
                        }
                    }
                }
            }
        },
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
            .padding(
                start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
                end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
                bottom = paddingValues.calculateBottomPadding(),
            ),
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
                verticalArrangement = Arrangement.Top,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                when (uiState.recorderState) {
                                    RecorderState.RECORDING -> Color(0xFFFFEEF0)
                                    RecorderState.PAUSED -> Color(0xFFFFF7E8)
                                    RecorderState.SAVING -> Color(0xFFEFF5FF)
                                    else -> Color(0xFFECFDF3)
                                },
                                RoundedCornerShape(100),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.FiberManualRecord,
                                contentDescription = null,
                                tint = when (uiState.recorderState) {
                                    RecorderState.RECORDING -> Color(0xFFEF4444)
                                    RecorderState.PAUSED -> Color(0xFFF59E0B)
                                    RecorderState.SAVING -> Color(0xFF2F80FF)
                                    else -> Color(0xFF16A34A)
                                },
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = when (uiState.recorderState) {
                                    RecorderState.RECORDING -> "Recording"
                                    RecorderState.PAUSED -> "Paused"
                                    RecorderState.SAVING -> "Saving"
                                    else -> "Ready"
                                },
                                color = when (uiState.recorderState) {
                                    RecorderState.RECORDING -> Color(0xFFEF4444)
                                    RecorderState.PAUSED -> Color(0xFFF59E0B)
                                    RecorderState.SAVING -> Color(0xFF2F80FF)
                                    else -> Color(0xFF16A34A)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            icon = Icons.Outlined.Mic,
                            contentDescription = "Mic",
                            selected = uiState.selectedMode == RecordingMode.MIC,
                            onClick = { onModeSelected(RecordingMode.MIC) },
                        )
                        ModeChip(
                            icon = Icons.Outlined.GraphicEq,
                            contentDescription = "App",
                            selected = uiState.selectedMode == RecordingMode.PLAYBACK_CAPTURE,
                            onClick = { onModeSelected(RecordingMode.PLAYBACK_CAPTURE) },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Text(
                        text = uiState.elapsedMs.formatDuration(),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF20263A),
                    )
                    Text("Recording time", color = Color(0xFF8A94A6))

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
                        )
                        RecorderControl(
                            label = "Start",
                            icon = Icons.Outlined.PlayArrow,
                            background = Color(0xFFEAF1FF),
                            enabled = (uiState.recorderState == RecorderState.IDLE || uiState.recorderState == RecorderState.ERROR) &&
                                !uiState.isStarting &&
                                !uiState.isAwaitingPlaybackConsent,
                            onClick = {
                                when (uiState.selectedMode) {
                                    RecordingMode.MIC -> {
                                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                            PackageManager.PERMISSION_GRANTED
                                        if (granted) onStart() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                    RecordingMode.PLAYBACK_CAPTURE -> {
                                        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                            PackageManager.PERMISSION_GRANTED
                                        if (!uiState.support.isSupported) {
                                            Log.d(tag, "Playback unsupported on start click: ${uiState.support.message}")
                                            onProjectionConsentDenied()
                                        } else if (!micGranted) {
                                            Log.d(tag, "Requesting RECORD_AUDIO for playback mode.")
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            val manager = projectionManager
                                            if (manager != null) {
                                                val launchId = onBeginPlaybackConsentRequest()
                                                Log.d(tag, "Launching fresh MediaProjection consent from start click launchId=$launchId.")
                                                projectionLauncher.launch(createProjectionIntent(manager))
                                            } else {
                                                Log.e(tag, "MediaProjectionManager is null on start click.")
                                                onProjectionLaunchUnavailable()
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

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
private fun ModeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (selected) Color(0xFF2F80FF) else Color(0xFFEAF1FF),
                RoundedCornerShape(100),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) Color.White else Color(0xFF2F80FF),
            modifier = Modifier.size(18.dp),
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
            .padding(start = 22.dp, end = 22.dp, top = 56.dp, bottom = 30.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Color.White)
                    Text("Sonic Note", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Record, transcribe, summarize.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box(Modifier.size(48.dp))
            }
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
    iconTint: Color = Color(0xFF405067),
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(68.dp)
                .background(background.copy(alpha = if (enabled) 1f else 0.45f), CircleShape),
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(30.dp))
        }
        Text(label, color = Color(0xFF7B8598), style = MaterialTheme.typography.bodySmall)
    }
}

private fun createProjectionIntent(manager: MediaProjectionManager): Intent {
    if (Build.VERSION.SDK_INT >= 34) {
        val configuredIntent = runCatching {
            val configClass = Class.forName("android.media.projection.MediaProjectionConfig")
            val createConfig = configClass.getMethod("createConfigForDefaultDisplay")
            val config = createConfig.invoke(null)
            val createIntent = MediaProjectionManager::class.java.getMethod(
                "createScreenCaptureIntent",
                configClass,
            )
            createIntent.invoke(manager, config) as Intent
        }.getOrNull()
        if (configuredIntent != null) return configuredIntent
    }
    return manager.createScreenCaptureIntent()
}
