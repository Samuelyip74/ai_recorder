package com.example.airecorder.domain.repository

import com.example.airecorder.domain.model.AppPreferences
import com.example.airecorder.domain.model.SummaryType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val preferences: Flow<AppPreferences>
    suspend fun setAutoTranscribe(enabled: Boolean)
    suspend fun setAutoSummary(enabled: Boolean)
    suspend fun setSummaryType(type: SummaryType)
    suspend fun setTranscriptionLanguage(language: String)
}
