package com.example.airecorder.transcription

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class PlaceholderTranscriptGenerator @Inject constructor() : TranscriptGenerator {
    override suspend fun generate(
        meetingId: Long,
        audioFilePath: String,
        language: String,
    ): Result<String> = runCatching {
        delay(1200)
        "Local transcript placeholder for meeting #$meetingId in language '$language'. Replace TranscriptGenerator with an on-device speech engine later."
    }
}
