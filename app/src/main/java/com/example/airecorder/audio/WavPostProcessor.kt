package com.example.airecorder.audio

import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class WavPostProcessor @Inject constructor() {

    fun reduceNoiseOnly(filePath: String) {
        RandomAccessFile(filePath, "rw").use { file ->
            require(readAscii(file, 0, 4) == "RIFF") { "Invalid WAV header." }
            require(readAscii(file, 8, 4) == "WAVE") { "Invalid WAV format." }

            val channels = readShortLE(file, 22)
            val bitsPerSample = readShortLE(file, 34)
            require(channels == 1) { "Only mono WAV files are supported." }
            require(bitsPerSample == 16) { "Only PCM16 WAV files are supported." }

            val dataLength = readIntLE(file, 40)
            if (dataLength <= 0) return

            file.seek(44)
            val pcm = ByteArray(dataLength)
            file.readFully(pcm)

            val samples = ShortArray(dataLength / 2)
            var sampleIndex = 0
            var byteIndex = 0
            while (byteIndex + 1 < pcm.size) {
                samples[sampleIndex++] = (((pcm[byteIndex + 1].toInt() shl 8) or (pcm[byteIndex].toInt() and 0xFF))).toShort()
                byteIndex += 2
            }

            if (samples.isEmpty()) return

            val processed = applyNoiseReduction(samples)

            file.seek(44)
            val output = ByteArray(processed.size * 2)
            byteIndex = 0
            processed.forEach { value ->
                output[byteIndex++] = (value.toInt() and 0xFF).toByte()
                output[byteIndex++] = ((value.toInt() shr 8) and 0xFF).toByte()
            }
            file.write(output)
        }
    }

    private fun applyNoiseReduction(input: ShortArray): ShortArray {
        val sampleWindow = input.take((44100 * 0.25f).toInt().coerceAtMost(input.size)).map { abs(it.toInt()) }
        val baseline = if (sampleWindow.isEmpty()) 0f else sampleWindow.average().toFloat()
        val floor = max(500, (baseline * 2.2f).toInt())
        val transition = floor * 3

        return ShortArray(input.size) { index ->
            val sample = input[index].toInt()
            val magnitude = abs(sample)
            val reduced = when {
                magnitude <= floor -> 0
                magnitude < transition -> {
                    val scaled = ((magnitude - floor).toFloat() / (transition - floor).toFloat()).coerceIn(0f, 1f)
                    ((sample.signAware()) * (magnitude - floor) * scaled).toInt()
                }
                else -> sample.signAware() * (magnitude - floor / 2)
            }
            reduced.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun Int.signAware(): Int = if (this < 0) -1 else 1

    private fun readAscii(file: RandomAccessFile, offset: Long, length: Int): String {
        file.seek(offset)
        val buffer = ByteArray(length)
        file.readFully(buffer)
        return String(buffer)
    }

    private fun readIntLE(file: RandomAccessFile, offset: Long): Int {
        file.seek(offset)
        val b0 = file.read()
        val b1 = file.read()
        val b2 = file.read()
        val b3 = file.read()
        return (b0 and 0xFF) or
            ((b1 and 0xFF) shl 8) or
            ((b2 and 0xFF) shl 16) or
            ((b3 and 0xFF) shl 24)
    }

    private fun readShortLE(file: RandomAccessFile, offset: Long): Int {
        file.seek(offset)
        val b0 = file.read()
        val b1 = file.read()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
    }
}
