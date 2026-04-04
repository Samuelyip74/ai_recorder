package com.example.airecorder.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureModel(language: String): File {
        val assetPath = resolveAssetPath(language)
        val targetFile = File(context.filesDir, assetPath)
        if (!targetFile.exists() || targetFile.length() <= 0L) {
            targetFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!targetFile.exists() || targetFile.length() <= 0L) {
            throw ModelNotInstalledException("Whisper model copy failed for $assetPath.")
        }
        return targetFile
    }

    private fun resolveAssetPath(language: String): String {
        val candidates = assetPathCandidates(language)
        return candidates.firstOrNull(::assetFileExists)
            ?: throw ModelNotInstalledException(
                "No Whisper model found for '$language'. Add a model file under app/src/main/assets/models, for example 'ggml-base.en.bin' or 'ggml-base.bin'.",
            )
    }

    private fun assetPathCandidates(language: String): List<String> {
        val normalized = language.lowercase()
        return if (normalized.startsWith("en")) {
            listOf(
                "models/ggml-base.en.bin",
                "models/ggml-small.en.bin",
                "models/ggml-tiny.en.bin",
                "models/ggml-base.bin",
                "models/ggml-small.bin",
                "models/ggml-tiny.bin",
            )
        } else {
            listOf(
                "models/ggml-base.bin",
                "models/ggml-small.bin",
                "models/ggml-tiny.bin",
            )
        }
    }

    private fun assetFileExists(assetPath: String): Boolean {
        val separatorIndex = assetPath.lastIndexOf('/')
        val parent = if (separatorIndex >= 0) assetPath.substring(0, separatorIndex) else ""
        val fileName = if (separatorIndex >= 0) assetPath.substring(separatorIndex + 1) else assetPath
        return runCatching {
            context.assets.list(parent).orEmpty().contains(fileName)
        }.getOrDefault(false)
    }
}
