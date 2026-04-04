package com.example.airecorder

import android.app.Application
import android.util.Log
import com.example.airecorder.domain.repository.SettingsRepository
import com.example.airecorder.transcription.WhisperContextManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class AiRecorderApp : Application() {

    companion object {
        private const val TAG = "AiRecorderApp"
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var whisperContextManager: WhisperContextManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching {
                val language = settingsRepository.preferences.first().transcriptionLanguage
                whisperContextManager.preload(language)
                Log.d(TAG, "Preloaded Whisper context for language=$language")
            }.onFailure {
                Log.w(TAG, "Whisper preload skipped", it)
            }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}
