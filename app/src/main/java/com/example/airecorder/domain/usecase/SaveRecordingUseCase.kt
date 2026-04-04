package com.example.airecorder.domain.usecase

import com.example.airecorder.domain.model.AppPreferences
import com.example.airecorder.domain.model.RecordingMode
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.domain.repository.SettingsRepository
import com.example.airecorder.domain.repository.TranscriptRepository
import com.example.airecorder.transcription.TranscriptGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveRecordingUseCase @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val settingsRepository: SettingsRepository,
    private val transcriptRepository: TranscriptRepository,
    private val transcriptGenerator: TranscriptGenerator,
) {
    suspend operator fun invoke(
        name: String,
        tempFilePath: String,
        tempWhisperFilePath: String,
        durationMs: Long,
        fileSizeBytes: Long,
        recordingMode: RecordingMode,
        captureNotes: String,
    ): Result<Long> = runCatching {
        val meetingId = meetingRepository.createMeeting(
            name = name,
            tempFilePath = tempFilePath,
            tempWhisperFilePath = tempWhisperFilePath,
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes,
            recordingMode = recordingMode,
            captureNotes = captureNotes,
        )
        val preferences = settingsRepository.preferences.first()
        maybeGenerateTranscript(meetingId, preferences)
        meetingId
    }

    private suspend fun maybeGenerateTranscript(meetingId: Long, preferences: AppPreferences) {
        if (!preferences.autoTranscribe) return
        val detail = meetingRepository.observeMeetingDetail(meetingId).first() ?: return
        transcriptRepository.upsertProcessing(meetingId, preferences.transcriptionLanguage)
        val transcriptResult = transcriptGenerator.generate(
            meetingId = meetingId,
            audioFilePath = detail.meeting.whisperAudioFilePath,
            language = preferences.transcriptionLanguage,
        )
        val transcript = transcriptResult.getOrElse {
            transcriptRepository.markFailed(meetingId, preferences.transcriptionLanguage)
            return
        }
        transcriptRepository.saveCompleted(meetingId, transcript, preferences.transcriptionLanguage)
    }

    suspend fun generateTranscript(meetingId: Long, language: String): Result<Unit> = runCatching {
        val detail = meetingRepository.observeMeetingDetail(meetingId).first() ?: error("Meeting not found")
        transcriptRepository.upsertProcessing(meetingId, language)
        transcriptGenerator.generate(meetingId, detail.meeting.whisperAudioFilePath, language)
            .onSuccess { transcriptRepository.saveCompleted(meetingId, it, language) }
            .onFailure { transcriptRepository.markFailed(meetingId, language) }
            .getOrThrow()
    }
}
