package com.example.airecorder.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPcmDecoder @Inject constructor() {

    fun decodeToMonoFloat32(
        audioFilePath: String,
        targetSampleRateHz: Int = 16_000,
    ): FloatArray {
        val rawSamples = decodeToMonoFloat32Raw(audioFilePath, targetSampleRateHz)
        return preprocessForSpeech(rawSamples, targetSampleRateHz)
    }

    fun decodeToMonoFloat32Raw(
        audioFilePath: String,
        targetSampleRateHz: Int = 16_000,
    ): FloatArray {
        var sourceSampleRateHz = targetSampleRateHz
        val pcmOutput = ByteArrayOutputStream()
        decodeToMonoPcm16(
            audioFilePath = audioFilePath,
            onAudioFormat = { sampleRateHz ->
                sourceSampleRateHz = sampleRateHz
            },
            onPcmChunk = { chunk ->
                pcmOutput.write(chunk)
            },
        )

        val monoPcm16 = pcmOutput.toByteArray()
        if (monoPcm16.isEmpty()) return FloatArray(0)

        val sourceSamples = ByteBuffer.wrap(monoPcm16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .let { shortBuffer ->
                FloatArray(shortBuffer.remaining()) { index ->
                    shortBuffer.get(index) / Short.MAX_VALUE.toFloat()
                }
            }

        return if (sourceSampleRateHz == targetSampleRateHz) {
            sourceSamples
        } else {
            resampleToTargetRate(sourceSamples, sourceSampleRateHz, targetSampleRateHz)
        }
    }

    fun decodeToMonoPcm16(
        audioFilePath: String,
        onAudioFormat: (sampleRateHz: Int) -> Unit,
        onPcmChunk: (ByteArray) -> Unit,
    ) {
        if (audioFilePath.lowercase().endsWith(".wav")) {
            decodeWaveFile(audioFilePath, onAudioFormat, onPcmChunk)
            return
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(audioFilePath)

        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track found in recording.")

        extractor.selectTrack(trackIndex)
        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME type missing.")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var outputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var outputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var formatSent = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: error("Decoder input buffer unavailable.")
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        outputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        if (!formatSent) {
                            onAudioFormat(outputSampleRate)
                            formatSent = true
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputBufferIndex >= 0) {
                            if (!formatSent) {
                                onAudioFormat(outputSampleRate)
                                formatSent = true
                            }
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val pcmChunk = outputBuffer.slice().toMonoPcm16(
                                    channelCount = outputChannels,
                                    pcmEncoding = pcmEncoding,
                                )
                                if (pcmChunk.isNotEmpty()) {
                                    onPcmChunk(pcmChunk)
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
    }

    private fun ByteBuffer.toMonoPcm16(channelCount: Int, pcmEncoding: Int): ByteArray {
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> toMonoPcm16FromInt16(channelCount)
            AudioFormat.ENCODING_PCM_FLOAT -> toMonoPcm16FromFloat(channelCount)
            else -> error("Unsupported PCM encoding: $pcmEncoding")
        }
    }

    private fun ByteBuffer.toMonoPcm16FromInt16(channelCount: Int): ByteArray {
        val buffer = order(ByteOrder.LITTLE_ENDIAN)
        val samplesPerChannel = remaining() / 2 / channelCount
        val output = ByteArray(samplesPerChannel * 2)
        var outIndex = 0
        repeat(samplesPerChannel) {
            var mixed = 0
            repeat(channelCount) {
                mixed += buffer.short.toInt()
            }
            val monoSample = (mixed / channelCount).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            output[outIndex++] = (monoSample.toInt() and 0xFF).toByte()
            output[outIndex++] = ((monoSample.toInt() shr 8) and 0xFF).toByte()
        }
        return output
    }

    private fun ByteBuffer.toMonoPcm16FromFloat(channelCount: Int): ByteArray {
        val buffer = order(ByteOrder.LITTLE_ENDIAN)
        val samplesPerChannel = remaining() / 4 / channelCount
        val output = ByteArray(samplesPerChannel * 2)
        var outIndex = 0
        repeat(samplesPerChannel) {
            var mixed = 0f
            repeat(channelCount) {
                mixed += buffer.float
            }
            val normalized = (mixed / channelCount).coerceIn(-1f, 1f)
            val monoSample = (normalized * Short.MAX_VALUE).toInt().toShort()
            output[outIndex++] = (monoSample.toInt() and 0xFF).toByte()
            output[outIndex++] = ((monoSample.toInt() shr 8) and 0xFF).toByte()
        }
        return output
    }

    private fun decodeWaveFile(
        audioFilePath: String,
        onAudioFormat: (sampleRateHz: Int) -> Unit,
        onPcmChunk: (ByteArray) -> Unit,
    ) {
        RandomAccessFile(File(audioFilePath), "r").use { file ->
            val riff = ByteArray(4)
            file.readFully(riff)
            require(String(riff) == "RIFF") { "Invalid WAV file header." }
            file.skipBytes(4)
            val wave = ByteArray(4)
            file.readFully(wave)
            require(String(wave) == "WAVE") { "Invalid WAV file format." }

            var channels = 1
            var sampleRate = 16_000
            var bitsPerSample = 16
            var dataLength = 0
            var dataOffset = 0L

            while (file.filePointer < file.length()) {
                val chunkId = ByteArray(4)
                file.readFully(chunkId)
                val chunkSize = Integer.reverseBytes(file.readInt())
                when (String(chunkId)) {
                    "fmt " -> {
                        file.skipBytes(2) // audio format
                        channels = java.lang.Short.reverseBytes(file.readShort()).toInt()
                        sampleRate = Integer.reverseBytes(file.readInt())
                        file.skipBytes(6)
                        bitsPerSample = java.lang.Short.reverseBytes(file.readShort()).toInt()
                        val remaining = chunkSize - 16
                        if (remaining > 0) file.skipBytes(remaining)
                    }
                    "data" -> {
                        dataLength = chunkSize
                        dataOffset = file.filePointer
                        file.skipBytes(chunkSize)
                    }
                    else -> file.skipBytes(chunkSize)
                }
            }

            require(dataOffset > 0L) { "WAV file does not contain audio data." }
            require(bitsPerSample == 16) { "Only 16-bit WAV files are supported." }

            onAudioFormat(sampleRate)
            file.seek(dataOffset)
            var remainingBytes = dataLength
            val buffer = ByteArray(4096.coerceAtMost(remainingBytes))
            while (remainingBytes > 0) {
                val read = file.read(buffer, 0, minOf(buffer.size, remainingBytes))
                if (read <= 0) break
                remainingBytes -= read
                val pcmChunk = ByteBuffer.wrap(buffer, 0, read)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .toMonoPcm16FromInt16(channels)
                if (pcmChunk.isNotEmpty()) onPcmChunk(pcmChunk)
            }
        }
    }

    private fun resampleToTargetRate(
        sourceSamples: FloatArray,
        sourceSampleRateHz: Int,
        targetSampleRateHz: Int,
    ): FloatArray {
        if (sourceSamples.isEmpty()) return sourceSamples
        val ratio = targetSampleRateHz.toDouble() / sourceSampleRateHz.toDouble()
        val outputLength = (sourceSamples.size * ratio).toInt().coerceAtLeast(1)
        return FloatArray(outputLength) { outputIndex ->
            val sourcePosition = outputIndex / ratio
            val leftIndex = sourcePosition.toInt().coerceIn(0, sourceSamples.lastIndex)
            val rightIndex = (leftIndex + 1).coerceAtMost(sourceSamples.lastIndex)
            val fraction = (sourcePosition - leftIndex).toFloat()
            val left = sourceSamples[leftIndex]
            val right = sourceSamples[rightIndex]
            left + ((right - left) * fraction)
        }
    }

    private fun preprocessForSpeech(
        samples: FloatArray,
        sampleRateHz: Int,
    ): FloatArray {
        if (samples.isEmpty()) return samples
        val trimmed = trimSilence(samples, sampleRateHz)
        return normalizePeak(trimmed)
    }

    private fun trimSilence(
        samples: FloatArray,
        sampleRateHz: Int,
    ): FloatArray {
        if (samples.isEmpty()) return samples

        val threshold = 0.015f
        val paddingSamples = (sampleRateHz * 0.15f).toInt()
        var start = 0
        while (start < samples.size && kotlin.math.abs(samples[start]) < threshold) {
            start++
        }

        var end = samples.lastIndex
        while (end >= start && kotlin.math.abs(samples[end]) < threshold) {
            end--
        }

        if (start > end) return samples

        val trimmedStart = (start - paddingSamples).coerceAtLeast(0)
        val trimmedEnd = (end + paddingSamples).coerceAtMost(samples.lastIndex)
        return samples.copyOfRange(trimmedStart, trimmedEnd + 1)
    }

    private fun normalizePeak(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val peak = samples.maxOf { kotlin.math.abs(it) }
        if (peak < 1e-4f) return samples

        val targetPeak = 0.92f
        val gain = (targetPeak / peak).coerceAtMost(8f)
        if (gain <= 1.05f) return samples

        return FloatArray(samples.size) { index ->
            (samples[index] * gain).coerceIn(-1f, 1f)
        }
    }
}
