package com.example.airecorder.audio

interface AudioRecorder {
    suspend fun start(): Result<String>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<RecordedAudio>
    suspend fun cancel()
}

data class RecordedAudio(
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
)
