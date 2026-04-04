package com.example.airecorder.domain.usecase

import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.domain.repository.SettingsRepository
import com.example.airecorder.translation.TextTranslator
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class TranslateTranscriptUseCase @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val settingsRepository: SettingsRepository,
    private val textTranslator: TextTranslator,
) {
    suspend operator fun invoke(
        meetingId: Long,
        targetLanguage: String? = null,
    ): Result<String> = runCatching {
        val detail = meetingRepository.observeMeetingDetail(meetingId).first() ?: error("Meeting not found")
        val transcript = detail.transcript?.text?.takeIf { it.isNotBlank() }
            ?: error("Transcript is required before translation")
        val sourceLanguage = detail.transcript.language
        val resolvedTarget = targetLanguage ?: settingsRepository.preferences.first().translationTargetLanguage

        require(sourceLanguage != resolvedTarget) { "Source and target languages must be different." }
        require(textTranslator.isLanguageSupported(sourceLanguage)) { "Unsupported source language: $sourceLanguage" }
        require(textTranslator.isLanguageSupported(resolvedTarget)) { "Unsupported target language: $resolvedTarget" }

        textTranslator.translate(
            text = transcript,
            sourceLanguageTag = sourceLanguage,
            targetLanguageTag = resolvedTarget,
        ).getOrThrow()
    }
}
