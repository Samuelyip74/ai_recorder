package com.example.airecorder.rainbow

import android.content.Context
import com.ale.infra.manager.files.RainbowFileDescriptor
import com.ale.infra.rest.listeners.onFailure
import com.ale.infra.rest.listeners.onSuccess
import com.ale.rainbowsdk.RainbowSdk
import com.example.airecorder.domain.model.Meeting
import com.example.airecorder.transcription.WhisperReadyAudioPreparer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RainbowLinkedMeetingResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperReadyAudioPreparer: WhisperReadyAudioPreparer,
    private val rainbowBubbleRepository: RainbowBubbleRepository,
) {

    companion object {
        private val SUPPORTED_MEDIA_EXTENSIONS = setOf("wav", "mp3", "m4a", "mp4", "aac", "ogg", "opus", "webm")
    }

    private val sdk by lazy { RainbowSdk.instance() }

    suspend fun resolvePlaybackFile(meeting: Meeting): Result<File> = runCatching {
        if (!meeting.isRainbowLinked()) {
            return@runCatching meeting.requireLocalAudioFile()
        }

        val metadata = RainbowCaptureMetadata.from(meeting.captureNotes)
        val roomId = metadata.roomId ?: error("Rainbow room id is missing for this meeting.")
        val descriptor = resolveDescriptor(roomId, metadata)
        val downloadedFile = downloadFile(descriptor)
        val mediaFile = extractImportableMedia(downloadedFile, descriptor)
        copyToCache(
            sourceFile = mediaFile,
            fileNamePrefix = "rainbow_linked_playback_${meeting.id}",
        )
    }

    suspend fun resolveWhisperFile(meeting: Meeting): Result<File> = runCatching {
        if (!meeting.isRainbowLinked()) {
            return@runCatching meeting.requireLocalWhisperFile()
        }

        val playbackFile = resolvePlaybackFile(meeting).getOrThrow()
        whisperReadyAudioPreparer.prepareTempFile(
            sourceAudioFilePath = playbackFile.absolutePath,
            outputDirectory = context.cacheDir,
        )
    }

    private suspend fun downloadFile(descriptor: RainbowFileDescriptor): File {
        sdk.fileStorage().downloadFile(descriptor)
            .onFailure { failure ->
                throw IllegalStateException(failure.message.ifBlank { "Unable to download Rainbow recording." })
            }
        return sdk.fileStorage().getFileDownloaded(descriptor)
            ?: error("Rainbow recording download completed without a local file.")
    }

    private suspend fun resolveDescriptor(
        roomId: String,
        metadata: RainbowCaptureMetadata,
    ): RainbowFileDescriptor {
        metadata.recordingFileId?.let { recordingFileId ->
            var descriptor: RainbowFileDescriptor? = null
            sdk.fileStorage().fetchFileDescriptorById(recordingFileId)
                .onSuccess { descriptor = it }
            if (descriptor != null) {
                return descriptor as RainbowFileDescriptor
            }
        }

        return rainbowBubbleRepository.resolveRecording(
            roomId = roomId,
            conferenceRecordId = metadata.conferenceRecordId,
        ).descriptor
    }

    private fun extractImportableMedia(downloadedFile: File, descriptor: RainbowFileDescriptor): File {
        if (!descriptor.isArchiveType() && downloadedFile.extension.lowercase() != "zip") {
            return downloadedFile
        }

        val extractionDirectory = File(context.cacheDir, "rainbow_linked_${System.currentTimeMillis()}").apply { mkdirs() }
        val extractedCandidates = mutableListOf<File>()
        ZipInputStream(downloadedFile.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.isDirectory) return@forEach
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (extension !in SUPPORTED_MEDIA_EXTENSIONS) return@forEach
                val outputFile = File(extractionDirectory, File(entry.name).name)
                FileOutputStream(outputFile).use { output ->
                    zip.copyTo(output)
                }
                extractedCandidates += outputFile
            }
        }

        return extractedCandidates.maxByOrNull { it.length() }
            ?: error("No audio or video file was found inside the Rainbow archive.")
    }

    private fun copyToCache(sourceFile: File, fileNamePrefix: String): File {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith(fileNamePrefix) }
            ?.forEach(File::delete)
        val extension = sourceFile.extension.ifBlank { "m4a" }
        val target = File.createTempFile(fileNamePrefix, ".$extension", context.cacheDir)
        sourceFile.copyTo(target, overwrite = true)
        return target
    }
}

private data class RainbowCaptureMetadata(
    val source: String?,
    val roomId: String?,
    val conferenceRecordId: String?,
    val recordingFileId: String?,
) {
    companion object {
        fun from(captureNotes: String): RainbowCaptureMetadata {
            val values = captureNotes
                .split(',', ';')
                .mapNotNull { token ->
                    val separatorIndex = token.indexOf('=')
                    if (separatorIndex <= 0) return@mapNotNull null
                    val key = token.substring(0, separatorIndex).trim()
                    val value = token.substring(separatorIndex + 1).trim()
                    key to value
                }
                .toMap()
            return RainbowCaptureMetadata(
                source = values["source"],
                roomId = values["roomId"],
                conferenceRecordId = values["conferenceRecordId"],
                recordingFileId = values["recordingFileId"] ?: values["recordingId"],
            )
        }
    }
}

private fun Meeting.isRainbowLinked(): Boolean {
    return RainbowCaptureMetadata.from(captureNotes).source == "rainbow"
}

private fun Meeting.requireLocalAudioFile(): File {
    val file = File(audioFilePath)
    check(file.exists()) { "Recording file is no longer available on this device." }
    return file
}

private fun Meeting.requireLocalWhisperFile(): File {
    val file = File(whisperAudioFilePath)
    check(file.exists()) { "Transcript source audio is no longer available on this device." }
    return file
}
