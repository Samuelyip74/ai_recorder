package com.example.airecorder.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
class MicAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "MicAudioRecorder"
        private const val SAMPLE_RATE_HZ = 44_100
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private val AUDIO_SOURCES = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var writer: RandomAccessFile? = null
    private var activeFile: File? = null
    private var recordingJob: Job? = null
    private var bytesWritten: Long = 0L
    private var activeAudioSource: Int = MediaRecorder.AudioSource.MIC
    private var startTimestamp: Long = 0L
    private var accumulatedDurationMs: Long = 0L
    private var pausedAtMs: Long = 0L
    private var isPaused = false

    suspend fun start(): Result<String> = runCatching {
        if (audioRecord != null) error("Recorder is already active")

        val prepared = createAudioRecordWithFallback()
        val tempFile = File.createTempFile("recording_", ".wav", context.cacheDir)
        val randomAccessFile = RandomAccessFile(tempFile, "rw").apply {
            setLength(0L)
            writeWavHeader(this, 0L, SAMPLE_RATE_HZ, 1, 16)
        }

        prepared.audioRecord.startRecording()
        audioRecord = prepared.audioRecord
        activeAudioSource = prepared.audioSource
        writer = randomAccessFile
        activeFile = tempFile
        bytesWritten = 0L
        startTimestamp = System.currentTimeMillis()
        accumulatedDurationMs = 0L
        pausedAtMs = 0L
        isPaused = false

        recordingJob = scope.launch {
            val buffer = ByteArray(prepared.bufferSizeInBytes)
            while (isActive) {
                val record = audioRecord ?: break
                if (isPaused) {
                    delay(75)
                    continue
                }
                val bytesRead = record.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    writer?.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead
                }
            }
        }

        tempFile.absolutePath
    }.onFailure {
        cleanup()
    }

    suspend fun pause(): Result<Unit> = runCatching {
        val record = audioRecord ?: error("Recorder is not active")
        if (!isPaused) {
            pausedAtMs = System.currentTimeMillis()
            accumulatedDurationMs += max(0L, pausedAtMs - startTimestamp)
            isPaused = true
            record.stop()
        }
    }

    suspend fun resume(): Result<Unit> = runCatching {
        val record = audioRecord ?: error("Recorder is not active")
        if (isPaused) {
            record.startRecording()
            startTimestamp = System.currentTimeMillis()
            pausedAtMs = 0L
            isPaused = false
        }
    }

    suspend fun stop(): Result<RecordedAudio> = runCatching {
        val record = audioRecord ?: error("Recorder is not active")
        val file = activeFile ?: error("Recording file is missing")
        if (!isPaused) {
            accumulatedDurationMs += max(0L, System.currentTimeMillis() - startTimestamp)
        }

        recordingJob?.cancelAndJoin()
        recordingJob = null
        runCatching { record.stop() }
        record.release()
        audioRecord = null

        writer?.let {
            updateWavHeader(it, bytesWritten, SAMPLE_RATE_HZ, 1, 16)
            it.close()
        }
        writer = null
        activeFile = null

        RecordedAudio(
            filePath = file.absolutePath,
            durationMs = accumulatedDurationMs,
            fileSizeBytes = file.length(),
            recordingMode = RecordingMode.MIC,
            captureNotes = buildCaptureNotes(activeAudioSource),
        )
    }.onFailure {
        cleanup()
    }

    suspend fun cancel() {
        cleanup()
    }

    private suspend fun cleanup() {
        recordingJob?.cancelAndJoin()
        recordingJob = null
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        runCatching { writer?.close() }
        writer = null
        activeFile?.delete()
        activeFile = null
        bytesWritten = 0L
        accumulatedDurationMs = 0L
        pausedAtMs = 0L
        isPaused = false
    }

    private fun createAudioRecordWithFallback(): PreparedAudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_MASK, ENCODING)
        check(minBufferSize > 0) { "Unable to determine microphone buffer size." }
        val bufferSizeInBytes = minBufferSize * 4
        val errors = mutableListOf<String>()

        for (source in AUDIO_SOURCES) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    SAMPLE_RATE_HZ,
                    CHANNEL_MASK,
                    ENCODING,
                    bufferSizeInBytes,
                )
            }.getOrNull()

            if (candidate == null) {
                errors += "source=${sourceName(source)} create_failed"
                continue
            }

            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "Using audio source ${sourceName(source)} for speech recording.")
                return PreparedAudioRecord(
                    audioRecord = candidate,
                    audioSource = source,
                    bufferSizeInBytes = bufferSizeInBytes,
                )
            }

            errors += "source=${sourceName(source)} state=${candidate.state}"
            candidate.release()
        }

        error("Unable to initialize microphone source. ${errors.joinToString()}")
    }

    private fun buildCaptureNotes(audioSource: Int): String {
        return buildString {
            append("source=mic")
            append(";audioSource=").append(sourceName(audioSource))
            append(";fallbackOrder=").append(AUDIO_SOURCES.joinToString(",") { sourceName(it) })
            append(";format=wav;pcm16")
            append(";sampleRate=").append(SAMPLE_RATE_HZ)
            append(";channels=mono")
            append(";postNoiseReduction=disabled")
            append(";postGain=disabled")
        }
    }

    private fun sourceName(audioSource: Int): String {
        return when (audioSource) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "voice_recognition"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "voice_communication"
            MediaRecorder.AudioSource.MIC -> "mic"
            else -> "source_$audioSource"
        }
    }

    private data class PreparedAudioRecord(
        val audioRecord: AudioRecord,
        val audioSource: Int,
        val bufferSizeInBytes: Int,
    )
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
    file.writeMicIntLE((36 + audioLength).toInt())
    file.writeBytes("WAVE")
    file.writeBytes("fmt ")
    file.writeMicIntLE(16)
    file.writeMicShortLE(1)
    file.writeMicShortLE(channels.toShort().toInt())
    file.writeMicIntLE(sampleRate)
    file.writeMicIntLE(byteRate)
    file.writeMicShortLE((channels * bitsPerSample / 8).toShort().toInt())
    file.writeMicShortLE(bitsPerSample.toShort().toInt())
    file.writeBytes("data")
    file.writeMicIntLE(audioLength.toInt())
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

private fun RandomAccessFile.writeMicIntLE(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        ),
    )
}

private fun RandomAccessFile.writeMicShortLE(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        ),
    )
}
