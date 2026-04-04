package com.example.airecorder.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.airecorder.domain.model.AppPreferences
import com.example.airecorder.domain.model.SummaryType
import com.example.airecorder.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val dataStore = context.dataStore

    override val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            autoTranscribe = prefs[Keys.AUTO_TRANSCRIBE] ?: false,
            autoSummary = prefs[Keys.AUTO_SUMMARY] ?: false,
            summaryType = SummaryType.valueOf(prefs[Keys.SUMMARY_TYPE] ?: SummaryType.CONCISE.name),
            transcriptionLanguage = prefs[Keys.LANGUAGE] ?: "en",
            translationTargetLanguage = prefs[Keys.TRANSLATION_TARGET_LANGUAGE] ?: "es",
        )
    }

    override suspend fun setAutoTranscribe(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_TRANSCRIBE] = enabled }
    }

    override suspend fun setAutoSummary(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_SUMMARY] = enabled }
    }

    override suspend fun setSummaryType(type: SummaryType) {
        dataStore.edit { it[Keys.SUMMARY_TYPE] = type.name }
    }

    override suspend fun setTranscriptionLanguage(language: String) {
        dataStore.edit { it[Keys.LANGUAGE] = language }
    }

    override suspend fun setTranslationTargetLanguage(language: String) {
        dataStore.edit { it[Keys.TRANSLATION_TARGET_LANGUAGE] = language }
    }

    private object Keys {
        val AUTO_TRANSCRIBE = booleanPreferencesKey("auto_transcribe")
        val AUTO_SUMMARY = booleanPreferencesKey("auto_summary")
        val SUMMARY_TYPE = stringPreferencesKey("summary_type")
        val LANGUAGE = stringPreferencesKey("language")
        val TRANSLATION_TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")
    }
}
