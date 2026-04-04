package com.example.airecorder.transcription

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class WhisperTranscriptGenerator @Inject constructor(
    private val whisperContextManager: WhisperContextManager,
    private val pcmDecoder: MediaPcmDecoder,
) : TranscriptGenerator {

    companion object {
        private const val TAG = "WhisperTranscriptGenerator"
        private const val TARGET_SAMPLE_RATE_HZ = 16_000
    }

    override suspend fun generate(
        meetingId: Long,
        audioFilePath: String,
        language: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Starting transcript generation for meetingId=$meetingId language=$language file=$audioFilePath")
            val whisperContext = whisperContextManager.getContext(language)
            Log.d(TAG, "Using preloaded Whisper context for language=$language")
            val audioSamples = pcmDecoder.decodeToMonoFloat32(
                audioFilePath = audioFilePath,
                targetSampleRateHz = TARGET_SAMPLE_RATE_HZ,
            )
            require(audioSamples.isNotEmpty()) { "Recording contains no decodable audio samples." }

            whisperContext.transcribeData(
                data = audioSamples,
                language = language,
                printTimestamp = false,
            ).trim().also {
                if (it.isBlank()) {
                    error("Whisper returned an empty transcript. Confirm the recording contains speech and the selected model matches the spoken language.")
                }
                Log.d(TAG, "Transcript generation completed successfully. length=${it.length}")
            }
        }.onFailure {
            Log.e(TAG, "Transcript generation failed for meetingId=$meetingId", it)
        }
    }
}
