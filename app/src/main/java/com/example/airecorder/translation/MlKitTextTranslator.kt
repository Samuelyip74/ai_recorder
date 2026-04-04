package com.example.airecorder.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class MlKitTextTranslator @Inject constructor() : TextTranslator {

    override fun isLanguageSupported(languageTag: String): Boolean {
        return TranslateLanguage.fromLanguageTag(languageTag) != null
    }

    override suspend fun downloadModelIfNeeded(
        sourceLanguageTag: String,
        targetLanguageTag: String,
        requireWifi: Boolean,
    ): Result<Unit> = runCatching {
        val translator = createTranslator(sourceLanguageTag, targetLanguageTag)
        try {
            translator.downloadModelIfNeeded(downloadConditions(requireWifi)).await()
        } finally {
            translator.close()
        }
    }

    override suspend fun translate(
        text: String,
        sourceLanguageTag: String,
        targetLanguageTag: String,
        requireWifiForModelDownload: Boolean,
    ): Result<String> = runCatching {
        require(text.isNotBlank()) { "Text to translate cannot be blank." }
        val translator = createTranslator(sourceLanguageTag, targetLanguageTag)
        try {
            translator.downloadModelIfNeeded(downloadConditions(requireWifiForModelDownload)).await()
            translator.translate(text).await()
        } finally {
            translator.close()
        }
    }

    private fun createTranslator(
        sourceLanguageTag: String,
        targetLanguageTag: String,
    ): Translator {
        val sourceLanguage = TranslateLanguage.fromLanguageTag(sourceLanguageTag)
            ?: error("Unsupported source language: $sourceLanguageTag")
        val targetLanguage = TranslateLanguage.fromLanguageTag(targetLanguageTag)
            ?: error("Unsupported target language: $targetLanguageTag")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        return Translation.getClient(options)
    }

    private fun downloadConditions(requireWifi: Boolean): DownloadConditions {
        return DownloadConditions.Builder().apply {
            if (requireWifi) {
                requireWifi()
            }
        }.build()
    }
}
