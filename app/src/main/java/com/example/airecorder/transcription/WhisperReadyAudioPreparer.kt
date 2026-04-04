package com.example.airecorder.transcription

import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperReadyAudioPreparer @Inject constructor(
    private val pcmDecoder: MediaPcmDecoder,
) {
    companion object {
        private const val TARGET_SAMPLE_RATE_HZ = 16_000
    }

    fun prepareTempFile(sourceAudioFilePath: String, outputDirectory: File): File {
        val samples = pcmDecoder.decodeToMonoFloat32Raw(
            audioFilePath = sourceAudioFilePath,
            targetSampleRateHz = TARGET_SAMPLE_RATE_HZ,
        )
        require(samples.isNotEmpty()) { "Recording contains no decodable audio samples." }

        val outputFile = File.createTempFile("whisper_ready_", ".wav", outputDirectory)
        RandomAccessFile(outputFile, "rw").use { file ->
            val audioLength = samples.size * 2L
            writeWhisperWavHeader(file, audioLength)
            samples.forEach { sample ->
                val pcm16 = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                file.write(byteArrayOf((pcm16.toInt() and 0xFF).toByte(), ((pcm16.toInt() shr 8) and 0xFF).toByte()))
            }
        }
        return outputFile
    }

    private fun writeWhisperWavHeader(file: RandomAccessFile, audioLength: Long) {
        file.seek(0)
        val byteRate = TARGET_SAMPLE_RATE_HZ * 2
        file.writeBytes("RIFF")
        file.writeIntLE((36 + audioLength).toInt())
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        file.writeIntLE(16)
        file.writeShortLE(1)
        file.writeShortLE(1)
        file.writeIntLE(TARGET_SAMPLE_RATE_HZ)
        file.writeIntLE(byteRate)
        file.writeShortLE(2)
        file.writeShortLE(16)
        file.writeBytes("data")
        file.writeIntLE(audioLength.toInt())
    }
}

private fun RandomAccessFile.writeIntLE(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        ),
    )
}

private fun RandomAccessFile.writeShortLE(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        ),
    )
}
