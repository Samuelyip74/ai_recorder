package com.example.airecorder.domain.model

data class Meeting(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val recordingMode: RecordingMode,
    val captureNotes: String,
    val transcriptStatus: TranscriptStatus,
    val summaryStatus: SummaryStatus,
)

data class Transcript(
    val id: Long,
    val meetingId: Long,
    val text: String,
    val language: String,
    val timestampsJson: String?,
    val status: TranscriptStatus,
    val edited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Summary(
    val id: Long,
    val meetingId: Long,
    val text: String,
    val type: SummaryType,
    val status: SummaryStatus,
    val edited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MeetingDetail(
    val meeting: Meeting,
    val transcript: Transcript?,
    val summary: Summary?,
)

data class StorageStats(
    val totalBytes: Long,
    val audioBytes: Long,
    val textBytes: Long,
)

data class AppPreferences(
    val autoTranscribe: Boolean = false,
    val autoSummary: Boolean = false,
    val summaryType: SummaryType = SummaryType.CONCISE,
    val transcriptionLanguage: String = "en",
)

data class RecordingDraft(
    val tempFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val recordingMode: RecordingMode,
    val captureNotes: String,
)
