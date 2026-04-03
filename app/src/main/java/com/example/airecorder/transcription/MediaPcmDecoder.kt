package com.example.airecorder.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPcmDecoder @Inject constructor() {

    fun decodeToMonoPcm16(
        audioFilePath: String,
        onAudioFormat: (sampleRateHz: Int) -> Unit,
        onPcmChunk: (ByteArray) -> Unit,
    ) {
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
}
