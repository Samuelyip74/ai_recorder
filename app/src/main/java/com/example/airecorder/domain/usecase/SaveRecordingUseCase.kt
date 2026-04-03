package com.example.airecorder.domain.usecase

import com.example.airecorder.domain.model.AppPreferences
import com.example.airecorder.domain.model.SummaryType
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.domain.repository.SettingsRepository
import com.example.airecorder.domain.repository.SummaryRepository
import com.example.airecorder.domain.repository.TranscriptRepository
import com.example.airecorder.summary.SummaryGenerator
import com.example.airecorder.transcription.TranscriptGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveRecordingUseCase @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val settingsRepository: SettingsRepository,
    private val transcriptRepository: TranscriptRepository,
    private val summaryRepository: SummaryRepository,
    private val transcriptGenerator: TranscriptGenerator,
    private val summaryGenerator: SummaryGenerator,
) {
    suspend operator fun invoke(
        name: String,
        tempFilePath: String,
        durationMs: Long,
        fileSizeBytes: Long,
    ): Result<Long> = runCatching {
        val meetingId = meetingRepository.createMeeting(name, tempFilePath, durationMs, fileSizeBytes)
        val preferences = settingsRepository.preferences.first()
        maybeGenerateTranscriptAndSummary(meetingId, preferences)
        meetingId
    }

    private suspend fun maybeGenerateTranscriptAndSummary(meetingId: Long, preferences: AppPreferences) {
        if (!preferences.autoTranscribe) return
        val detail = meetingRepository.observeMeetingDetail(meetingId).first() ?: return
        transcriptRepository.upsertProcessing(meetingId, preferences.transcriptionLanguage)
        val transcriptResult = transcriptGenerator.generate(
            meetingId = meetingId,
            audioFilePath = detail.meeting.audioFilePath,
            language = preferences.transcriptionLanguage,
        )
        val transcript = transcriptResult.getOrElse {
            transcriptRepository.markFailed(meetingId, preferences.transcriptionLanguage)
            return
        }
        transcriptRepository.saveCompleted(meetingId, transcript, preferences.transcriptionLanguage)

        if (!preferences.autoSummary) return
        generateSummaryInternal(meetingId, transcript, preferences.summaryType)
    }

    suspend fun generateTranscript(meetingId: Long, language: String): Result<Unit> = runCatching {
        val detail = meetingRepository.observeMeetingDetail(meetingId).first() ?: error("Meeting not found")
        transcriptRepository.upsertProcessing(meetingId, language)
        transcriptGenerator.generate(meetingId, detail.meeting.audioFilePath, language)
            .onSuccess { transcriptRepository.saveCompleted(meetingId, it, language) }
            .onFailure { transcriptRepository.markFailed(meetingId, language) }
            .getOrThrow()
    }

    suspend fun generateSummary(meetingId: Long, type: SummaryType): Result<Unit> = runCatching {
        val transcript = transcriptRepository.getTranscript(meetingId)?.text
            ?: error("Transcript is required before summary generation")
        generateSummaryInternal(meetingId, transcript, type)
    }

    private suspend fun generateSummaryInternal(meetingId: Long, transcript: String, type: SummaryType) {
        summaryRepository.upsertProcessing(meetingId, type)
        summaryGenerator.generate(transcript, type)
            .onSuccess { summaryRepository.saveCompleted(meetingId, it, type) }
            .onFailure { summaryRepository.markFailed(meetingId, type) }
            .getOrThrow()
    }
}
