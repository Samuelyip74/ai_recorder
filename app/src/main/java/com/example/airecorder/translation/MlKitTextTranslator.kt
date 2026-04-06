package com.example.airecorder.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await

@Singleton
class MlKitTextTranslator @Inject constructor() : TextTranslator {

    companion object {
        private const val TRANSLATION_TIMEOUT_MS = 90_000L
    }

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
            withTimeout(TRANSLATION_TIMEOUT_MS) {
                translator.downloadModelIfNeeded(downloadConditions(requireWifi)).await()
                Unit
            }
        } finally {
            translator.close()
        }
    }.recoverCatching { throwable ->
        throw throwable.toReadableTranslationError()
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
            withTimeout(TRANSLATION_TIMEOUT_MS) {
                translator.downloadModelIfNeeded(downloadConditions(requireWifiForModelDownload)).await()
                translator.translate(text).await()
            }
        } finally {
            translator.close()
        }
    }.recoverCatching { throwable ->
        throw throwable.toReadableTranslationError()
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

    private fun Throwable.toReadableTranslationError(): Throwable {
        return when (this) {
            is TimeoutCancellationException -> IllegalStateException(
                "Translation timed out while downloading the language model or translating the transcript.",
                this,
            )
            else -> this
        }
    }
}
