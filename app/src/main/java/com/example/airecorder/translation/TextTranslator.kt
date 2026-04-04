package com.example.airecorder.translation

interface TextTranslator {
    fun isLanguageSupported(languageTag: String): Boolean

    suspend fun downloadModelIfNeeded(
        sourceLanguageTag: String,
        targetLanguageTag: String,
        requireWifi: Boolean = true,
    ): Result<Unit>

    suspend fun translate(
        text: String,
        sourceLanguageTag: String,
        targetLanguageTag: String,
        requireWifiForModelDownload: Boolean = true,
    ): Result<String>
}
