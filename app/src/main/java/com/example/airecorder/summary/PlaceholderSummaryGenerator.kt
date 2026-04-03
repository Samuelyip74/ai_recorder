package com.example.airecorder.summary

import com.example.airecorder.domain.model.SummaryType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class PlaceholderSummaryGenerator @Inject constructor() : SummaryGenerator {
    override suspend fun generate(transcript: String, type: SummaryType): Result<String> = runCatching {
        delay(900)
        val snippet = transcript.take(220).ifBlank { "No transcript text available." }
        when (type) {
            SummaryType.CONCISE -> "Concise summary:\n$snippet"
            SummaryType.KEY_POINTS -> "Key points:\n- ${snippet.take(90)}\n- Local-only placeholder summary\n- Replace SummaryGenerator for on-device AI"
            SummaryType.ACTION_ITEMS -> "Action items:\n- Review transcript\n- Confirm follow-ups\n- Replace placeholder summarizer when ready"
        }
    }
}
