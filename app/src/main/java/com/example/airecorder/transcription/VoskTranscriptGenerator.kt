package com.example.airecorder.transcription

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

@Singleton
class VoskTranscriptGenerator @Inject constructor(
    private val modelManager: VoskModelManager,
    private val pcmDecoder: MediaPcmDecoder,
) : TranscriptGenerator {

    companion object {
        private const val TAG = "VoskTranscriptGenerator"
    }

    override suspend fun generate(
        meetingId: Long,
        audioFilePath: String,
        language: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Starting transcript generation for meetingId=$meetingId language=$language file=$audioFilePath")
            val modelDir = modelManager.ensureModel(language)
            Log.d(TAG, "Using Vosk model at ${modelDir.absolutePath}")
            Model(modelDir.absolutePath).use { model ->
                var recognizer: Recognizer? = null
                try {
                    pcmDecoder.decodeToMonoPcm16(
                        audioFilePath = audioFilePath,
                        onAudioFormat = { sampleRateHz ->
                            Log.d(TAG, "Decoded audio sampleRateHz=$sampleRateHz")
                            recognizer = Recognizer(model, sampleRateHz.toFloat())
                        },
                        onPcmChunk = { chunk ->
                            val activeRecognizer = recognizer ?: error("Recognizer was not initialized.")
                            activeRecognizer.acceptWaveForm(chunk, chunk.size)
                        },
                    )
                    val finalRecognizer = recognizer ?: error("Unable to decode audio for transcription.")
                    extractTranscript(finalRecognizer.finalResult).also {
                        Log.d(TAG, "Transcript generation completed successfully. length=${it.length}")
                    }
                } finally {
                    recognizer?.close()
                }
            }
        }.onFailure {
            Log.e(TAG, "Transcript generation failed for meetingId=$meetingId", it)
        }
    }

    private fun extractTranscript(resultJson: String): String {
        val text = JSONObject(resultJson).optString("text").trim()
        if (text.isBlank()) {
            throw IllegalStateException(
                "On-device speech engine returned no transcript. Confirm the recording has audible speech and an installed offline model.",
            )
        }
        return text
    }
}
