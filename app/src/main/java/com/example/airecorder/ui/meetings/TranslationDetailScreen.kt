package com.example.airecorder.ui.meetings

import androidx.compose.runtime.Composable

@Composable
fun TranslationDetailScreen(
    uiState: MeetingDetailUiState,
    translatedTranscript: String,
    translationTargetLanguage: String,
    onBack: () -> Unit,
) {
    val detail = uiState.detail ?: return
    DocumentDetailScreen(
        title = "Translation (${translationTargetLanguage.uppercase()})",
        subtitle = detail.meeting.name,
        text = translatedTranscript.ifBlank { "No translation available." },
        shareFilenamePrefix = "${detail.meeting.name}_translation",
        shareSubject = "${detail.meeting.name} translation",
        chooserTitle = "Share translation",
        onBack = onBack,
    )
}
