package com.example.airecorder.audio

import android.content.Intent
import com.example.airecorder.domain.model.RecordingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAudioRecorder @Inject constructor(
    private val micAudioRecorder: MicAudioRecorder,
    private val playbackCaptureRecorder: PlaybackCaptureRecorder,
) : AudioRecorder {

    private var activeMode: RecordingMode? = null

    override fun getSupport(mode: RecordingMode): RecordingSupport {
        return when (mode) {
            RecordingMode.MIC -> RecordingSupport(
                isSupported = true,
                requiresMicrophonePermission = true,
                message = "Speech-optimized microphone capture with automatic source fallback.",
            )
            RecordingMode.PLAYBACK_CAPTURE -> playbackCaptureRecorder.getSupport()
        }
    }

    override suspend fun setPlaybackCaptureConsent(resultCode: Int, data: Intent?): Result<Unit> {
        return playbackCaptureRecorder.setConsent(resultCode, data)
    }

    override suspend fun start(mode: RecordingMode): Result<String> {
        activeMode = mode
        return when (mode) {
            RecordingMode.MIC -> micAudioRecorder.start()
            RecordingMode.PLAYBACK_CAPTURE -> playbackCaptureRecorder.start()
        }
    }

    override suspend fun pause(): Result<Unit> {
        return when (activeMode) {
            RecordingMode.MIC -> micAudioRecorder.pause()
            RecordingMode.PLAYBACK_CAPTURE -> playbackCaptureRecorder.pause()
            null -> Result.failure(IllegalStateException("Recorder is not active."))
        }
    }

    override suspend fun resume(): Result<Unit> {
        return when (activeMode) {
            RecordingMode.MIC -> micAudioRecorder.resume()
            RecordingMode.PLAYBACK_CAPTURE -> playbackCaptureRecorder.resume()
            null -> Result.failure(IllegalStateException("Recorder is not active."))
        }
    }

    override suspend fun stop(): Result<RecordedAudio> {
        val result = when (activeMode) {
            RecordingMode.MIC -> micAudioRecorder.stop()
            RecordingMode.PLAYBACK_CAPTURE -> playbackCaptureRecorder.stop()
            null -> Result.failure(IllegalStateException("Recorder is not active."))
        }
        activeMode = null
        return result
    }

    override suspend fun cancel() {
        when (activeMode) {
            RecordingMode.MIC -> micAudioRecorder.cancel()
            RecordingMode.PLAYBACK_CAPTURE -> playbackCaptureRecorder.cancel()
            null -> Unit
        }
        activeMode = null
    }
}
