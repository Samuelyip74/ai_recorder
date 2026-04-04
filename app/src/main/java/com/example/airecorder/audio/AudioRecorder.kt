package com.example.airecorder.audio

import android.content.Intent
import com.example.airecorder.domain.model.RecordingMode

interface AudioRecorder {
    fun getSupport(mode: RecordingMode): RecordingSupport
    fun getActiveInputRouteLabel(): String?
    suspend fun setPlaybackCaptureConsent(resultCode: Int, data: Intent?): Result<Unit>
    suspend fun start(mode: RecordingMode): Result<String>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<RecordedAudio>
    suspend fun cancel()
}

data class RecordingSupport(
    val isSupported: Boolean,
    val requiresMicrophonePermission: Boolean = false,
    val requiresProjectionConsent: Boolean = false,
    val message: String? = null,
)

data class RecordedAudio(
    val filePath: String,
    val whisperFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val recordingMode: RecordingMode,
    val captureNotes: String,
)
