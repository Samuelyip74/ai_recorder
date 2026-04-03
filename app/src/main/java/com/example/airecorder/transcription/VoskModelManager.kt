package com.example.airecorder.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoskModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureModel(language: String): File {
        val assetPath = resolveInstalledAssetPath(language)
        if (!assetDirectoryExists(assetPath)) {
            throw ModelNotInstalledException(
                "No on-device speech model found for '$language'. Add a Vosk model under app/src/main/assets/models.",
            )
        }

        val targetDir = File(context.filesDir, assetPath)
        if (!containsModelFiles(targetDir)) {
            targetDir.deleteRecursively()
            copyAssetDirectory(assetPath, targetDir)
        }

        val resolvedDir = resolveModelRoot(targetDir)
        if (!containsModelFiles(resolvedDir)) {
            throw ModelNotInstalledException(
                "Copied model folder is incomplete. Expected Vosk files like 'am', 'conf', 'graph', and 'ivector' under ${resolvedDir.absolutePath}.",
            )
        }
        return resolvedDir
    }

    private fun assetPathCandidates(language: String): List<String> {
        val normalized = language.lowercase()
        return when {
            normalized.startsWith("en") -> listOf(
                "models/vosk-en-us-small",
                "models/vosk-model-small-en-us-0.15",
            )
            normalized.startsWith("es") -> listOf(
                "models/vosk-es-small",
            )
            normalized.startsWith("fr") -> listOf(
                "models/vosk-fr-small",
            )
            normalized.startsWith("de") -> listOf(
                "models/vosk-de-small",
            )
            else -> listOf(
                "models/vosk-en-us-small",
                "models/vosk-model-small-en-us-0.15",
            )
        }
    }

    private fun resolveInstalledAssetPath(language: String): String {
        return assetPathCandidates(language).firstOrNull(::assetDirectoryExists)
            ?: assetPathCandidates(language).first()
    }

    private fun assetDirectoryExists(path: String): Boolean {
        return runCatching { context.assets.list(path).orEmpty().isNotEmpty() }.getOrDefault(false)
    }

    private fun copyAssetDirectory(assetPath: String, destination: File) {
        destination.mkdirs()
        val entries = context.assets.list(assetPath).orEmpty()
        entries.forEach { entry ->
            val childAssetPath = "$assetPath/$entry"
            val nestedEntries = context.assets.list(childAssetPath).orEmpty()
            val childFile = File(destination, entry)
            if (nestedEntries.isNotEmpty()) {
                copyAssetDirectory(childAssetPath, childFile)
            } else {
                context.assets.open(childAssetPath).use { input ->
                    childFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun containsModelFiles(directory: File): Boolean {
        val resolved = resolveModelRoot(directory)
        return resolved.exists() &&
            File(resolved, "am").exists() &&
            File(resolved, "conf").exists() &&
            File(resolved, "graph").exists() &&
            File(resolved, "ivector").exists()
    }

    private fun resolveModelRoot(directory: File): File {
        if (!directory.exists()) return directory
        if (File(directory, "am").exists()) return directory
        val children = directory.listFiles()?.filter { it.isDirectory } ?: return directory
        return if (children.size == 1 && File(children.first(), "am").exists()) {
            children.first()
        } else {
            directory
        }
    }
}
