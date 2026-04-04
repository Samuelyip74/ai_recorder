package com.example.airecorder.transcription

import com.whispercpp.whisper.WhisperContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class WhisperContextManager @Inject constructor(
    private val modelManager: WhisperModelManager,
) {
    private val mutex = Mutex()
    private var cachedModelPath: String? = null
    private var cachedContext: WhisperContext? = null

    suspend fun preload(language: String) {
        getContext(language)
    }

    suspend fun getContext(language: String): WhisperContext = mutex.withLock {
        val modelFile = modelManager.ensureModel(language)
        val modelPath = modelFile.absolutePath
        val existingContext = cachedContext
        if (existingContext != null && cachedModelPath == modelPath) {
            return existingContext
        }

        cachedContext?.release()
        val context = WhisperContext.createContextFromFile(modelPath)
        cachedContext = context
        cachedModelPath = modelPath
        context
    }

    suspend fun release() = mutex.withLock {
        cachedContext?.release()
        cachedContext = null
        cachedModelPath = null
    }
}
