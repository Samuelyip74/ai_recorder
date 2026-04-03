package com.example.airecorder.transcription

interface TranscriptGenerator {
    suspend fun generate(meetingId: Long, audioFilePath: String, language: String): Result<String>
}
