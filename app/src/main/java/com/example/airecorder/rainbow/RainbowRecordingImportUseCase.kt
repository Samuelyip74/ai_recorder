package com.example.airecorder.rainbow

import android.content.Context
import android.media.MediaMetadataRetriever
import com.ale.infra.manager.files.RainbowFileDescriptor
import com.ale.infra.rest.listeners.onFailure
import com.ale.infra.rest.listeners.onSuccess
import com.ale.rainbowsdk.RainbowSdk
import com.example.airecorder.domain.model.RecordingMode
import com.example.airecorder.domain.usecase.SaveRecordingUseCase
import com.example.airecorder.transcription.WhisperReadyAudioPreparer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RainbowRecordingImportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveRecordingUseCase: SaveRecordingUseCase,
    private val whisperReadyAudioPreparer: WhisperReadyAudioPreparer,
    private val rainbowBubbleRepository: RainbowBubbleRepository,
) {

    companion object {
        private val SUPPORTED_MEDIA_EXTENSIONS = setOf("wav", "mp3", "m4a", "mp4", "aac", "ogg", "opus", "webm")
    }

    private val sdk by lazy { RainbowSdk.instance() }

    suspend operator fun invoke(bubble: RainbowBubbleConversation): Result<Long> = runCatching {
        val resolvedRecording = rainbowBubbleRepository.resolveRecording(
            roomId = bubble.id,
            conferenceRecordId = bubble.latestRecordingId,
        )
        val descriptor = resolvedRecording.descriptor
        val downloadedFile = downloadFile(descriptor)
        val mediaFile = extractImportableMedia(downloadedFile, descriptor)
        val importedSource = copyToImportTemp(mediaFile)
        val whisperFile = whisperReadyAudioPreparer.prepareTempFile(
            sourceAudioFilePath = importedSource.absolutePath,
            outputDirectory = context.cacheDir,
        )
        val meetingName = descriptor.getFileNameWithoutExtension()
            .ifBlank { bubble.name.ifBlank { "Rainbow recording" } }
        saveRecordingUseCase(
            name = meetingName,
            tempFilePath = importedSource.absolutePath,
            tempWhisperFilePath = whisperFile.absolutePath,
            durationMs = resolveDurationMs(importedSource),
            fileSizeBytes = importedSource.length(),
            recordingMode = RecordingMode.PLAYBACK_CAPTURE,
            captureNotes = buildCaptureNotes(
                bubble = bubble,
                descriptor = descriptor,
                conferenceRecordId = resolvedRecording.conferenceRecordId,
            ),
        ).getOrThrow()
    }

    private suspend fun downloadFile(descriptor: RainbowFileDescriptor): File {
        sdk.fileStorage().downloadFile(descriptor)
            .onFailure { failure ->
                throw IllegalStateException(failure.message.ifBlank { "Unable to download Rainbow recording." })
            }
        return sdk.fileStorage().getFileDownloaded(descriptor)
            ?: error("Rainbow recording download completed without a local file.")
    }

    private fun extractImportableMedia(downloadedFile: File, descriptor: RainbowFileDescriptor): File {
        if (!descriptor.isArchiveType() && downloadedFile.extension.lowercase() != "zip") {
            return downloadedFile
        }

        val extractionDirectory = File(context.cacheDir, "rainbow_import_${System.currentTimeMillis()}").apply { mkdirs() }
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

    private fun copyToImportTemp(sourceFile: File): File {
        val extension = sourceFile.extension.ifBlank { "wav" }
        val target = File.createTempFile("rainbow_import_", ".$extension", context.cacheDir)
        sourceFile.copyTo(target, overwrite = true)
        return target
    }

    private fun resolveDurationMs(audioFile: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    private fun buildCaptureNotes(
        bubble: RainbowBubbleConversation,
        descriptor: RainbowFileDescriptor,
        conferenceRecordId: String,
    ): String {
        return buildString {
            append("source=rainbow")
            append(";roomId=").append(bubble.id)
            append(";conferenceRecordId=").append(conferenceRecordId)
            descriptor.id?.let { append(";recordingFileId=").append(it) }
            descriptor.ownerId?.let { append(";ownerId=").append(it) }
            append(";roomName=").append(bubble.name.replace(";", " "))
        }
    }
}
