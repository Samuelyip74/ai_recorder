package com.example.airecorder.audio

import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null
    private var startTimestamp: Long = 0L
    private var accumulatedDurationMs: Long = 0L
    private var pausedAtMs: Long = 0L

    override suspend fun start(): Result<String> = runCatching {
        if (recorder != null) error("Recorder is already active")
        val tempFile = File.createTempFile("recording_", ".m4a", context.cacheDir)
        val mediaRecorder = buildRecorder(tempFile)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
        activeFile = tempFile
        startTimestamp = System.currentTimeMillis()
        accumulatedDurationMs = 0L
        pausedAtMs = 0L
        tempFile.absolutePath
    }

    override suspend fun pause(): Result<Unit> = runCatching {
        recorder?.pause() ?: error("Recorder is not active")
        pausedAtMs = System.currentTimeMillis()
        accumulatedDurationMs += max(0L, pausedAtMs - startTimestamp)
    }

    override suspend fun resume(): Result<Unit> = runCatching {
        recorder?.resume() ?: error("Recorder is not active")
        startTimestamp = System.currentTimeMillis()
        pausedAtMs = 0L
    }

    override suspend fun stop(): Result<RecordedAudio> = runCatching {
        val currentRecorder = recorder ?: error("Recorder is not active")
        val file = activeFile ?: error("Recording file is missing")
        if (pausedAtMs == 0L) {
            accumulatedDurationMs += max(0L, System.currentTimeMillis() - startTimestamp)
        }
        currentRecorder.stop()
        currentRecorder.reset()
        currentRecorder.release()
        recorder = null
        activeFile = null
        RecordedAudio(
            filePath = file.absolutePath,
            durationMs = accumulatedDurationMs,
            fileSizeBytes = file.length(),
        )
    }.onFailure {
        cleanupRecorder()
    }

    override suspend fun cancel() {
        cleanupRecorder()
    }

    private fun cleanupRecorder() {
        runCatching {
            recorder?.reset()
            recorder?.release()
        }
        recorder = null
        activeFile?.delete()
        activeFile = null
        accumulatedDurationMs = 0L
        pausedAtMs = 0L
    }

    private fun buildRecorder(file: File): MediaRecorder {
        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
        }
    }
}
