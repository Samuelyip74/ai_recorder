package com.example.airecorder.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import com.example.airecorder.domain.model.RecordingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

@Singleton
class PlaybackCaptureRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PlaybackCaptureRecorder"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val projectionManager = context.getSystemService(MediaProjectionManager::class.java)

    private var projectionResultCode: Int? = null
    private var projectionData: Intent? = null
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var activeFile: File? = null
    private var writer: RandomAccessFile? = null
    private var bytesWritten: Long = 0L
    private var recordingJob: Job? = null
    private var isPaused = false
    private var startedAtMs = 0L
    private var accumulatedDurationMs = 0L

    fun getSupport(): RecordingSupport {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            RecordingSupport(
                isSupported = false,
                message = "Playback capture requires Android 10 or later.",
            )
        } else {
            RecordingSupport(
                isSupported = true,
                requiresMicrophonePermission = true,
                requiresProjectionConsent = true,
                message = "Records device audio for apps that allow playback capture.",
            )
        }
    }

    fun getActiveInputRouteLabel(): String? {
        return if (audioRecord != null) "App audio" else null
    }

    suspend fun setConsent(resultCode: Int, data: Intent?): Result<Unit> = runCatching {
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            Log.d(TAG, "Received fresh MediaProjection consent.")
            projectionResultCode = resultCode
            projectionData = Intent(data)
        } else {
            error("Playback capture permission was denied.")
        }
    }

    suspend fun start(): Result<String> = runCatching {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Playback capture requires Android 10 or later." }
        val consentData = projectionData ?: error("Playback capture permission is required.")
        val projectionCode = projectionResultCode ?: error("Playback capture permission is required.")
        if (recordingJob != null) error("Playback capture is already running.")

        Log.d(TAG, "Starting playback capture foreground service and waiting for readiness.")
        PlaybackCaptureService.startAndAwaitReady(context)
        val tempFile = File.createTempFile("playback_capture_", ".wav", context.cacheDir)
        Log.d(TAG, "Requesting MediaProjection token.")
        val activeProjection = projectionManager.getMediaProjection(projectionCode, Intent(consentData))
        projectionResultCode = null
        projectionData = null
        Log.d(TAG, "MediaProjection token acquired.")
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(
            activeProjection,
        )
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_MASK, ENCODING)
        check(minBuffer > 0) { "Unable to initialize playback capture buffer." }
        val record = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(CHANNEL_MASK)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        mediaProjection = activeProjection
        activeFile = tempFile
        writer = RandomAccessFile(tempFile, "rw").apply { setLength(0L); writeWavHeader(this, 0, SAMPLE_RATE_HZ, 1, 16) }
        audioRecord = record
        bytesWritten = 0L
        accumulatedDurationMs = 0L
        startedAtMs = System.currentTimeMillis()
        isPaused = false
        record.startRecording()
        Log.d(TAG, "Playback AudioRecord started.")
        recordingJob = scope.launch {
            val buffer = ByteArray(minBuffer)
            while (isActive && audioRecord != null) {
                if (isPaused) {
                    delay(100)
                    continue
                }
                val bytesRead = record.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    writer?.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead
                }
            }
        }
        Log.d(TAG, "Playback capture writing to ${tempFile.absolutePath}")
        tempFile.absolutePath
    }.onFailure { throwable ->
        Log.e(TAG, "Playback capture start failed before recording became active.", throwable)
    }

    suspend fun pause(): Result<Unit> = runCatching {
        val record = audioRecord ?: error("Playback capture is not active.")
        if (!isPaused) {
            accumulatedDurationMs += max(0L, System.currentTimeMillis() - startedAtMs)
            isPaused = true
            record.stop()
        }
    }

    suspend fun resume(): Result<Unit> = runCatching {
        val record = audioRecord ?: error("Playback capture is not active.")
        if (isPaused) {
            startedAtMs = System.currentTimeMillis()
            isPaused = false
            record.startRecording()
        }
    }

    suspend fun stop(): Result<RecordedAudio> = runCatching {
        val file = activeFile ?: error("Playback capture file is missing.")
        if (!isPaused) {
            accumulatedDurationMs += max(0L, System.currentTimeMillis() - startedAtMs)
        }
        recordingJob?.cancelAndJoin()
        recordingJob = null
        audioRecord?.runCatching { stop() }
        audioRecord?.release()
        audioRecord = null
        writer?.let {
            updateWavHeader(it, bytesWritten, SAMPLE_RATE_HZ, 1, 16)
            it.close()
        }
        writer = null
        mediaProjection?.stop()
        mediaProjection = null
        PlaybackCaptureService.stop(context)
        RecordedAudio(
            filePath = file.absolutePath,
            whisperFilePath = file.absolutePath,
            durationMs = accumulatedDurationMs,
            fileSizeBytes = file.length(),
            recordingMode = RecordingMode.PLAYBACK_CAPTURE,
            captureNotes = "source=playback_capture;format=wav;pcm16;sampleRate=$SAMPLE_RATE_HZ",
        )
    }.onFailure {
        cancel()
    }

    suspend fun cancel() {
        recordingJob?.cancelAndJoin()
        recordingJob = null
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        writer?.close()
        writer = null
        activeFile?.delete()
        activeFile = null
        mediaProjection?.stop()
        mediaProjection = null
        projectionResultCode = null
        projectionData = null
        PlaybackCaptureService.stop(context)
        bytesWritten = 0L
        accumulatedDurationMs = 0L
        isPaused = false
    }

    private fun writeWavHeader(
        file: RandomAccessFile,
        audioLength: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ) {
        file.seek(0)
        val byteRate = sampleRate * channels * bitsPerSample / 8
        file.writeBytes("RIFF")
        file.writeIntLE((36 + audioLength).toInt())
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        file.writeIntLE(16)
        file.writeShortLE(1)
        file.writeShortLE(channels.toShort().toInt())
        file.writeIntLE(sampleRate)
        file.writeIntLE(byteRate)
        file.writeShortLE((channels * bitsPerSample / 8).toShort().toInt())
        file.writeShortLE(bitsPerSample.toShort().toInt())
        file.writeBytes("data")
        file.writeIntLE(audioLength.toInt())
    }

    private fun updateWavHeader(
        file: RandomAccessFile,
        audioLength: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ) {
        writeWavHeader(file, audioLength, sampleRate, channels, bitsPerSample)
    }
}

private fun RandomAccessFile.writeIntLE(value: Int) {
    write(byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    ))
}

private fun RandomAccessFile.writeShortLE(value: Int) {
    write(byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    ))
}
