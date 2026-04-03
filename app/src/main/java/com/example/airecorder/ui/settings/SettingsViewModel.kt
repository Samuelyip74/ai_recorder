package com.example.airecorder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.domain.model.AppPreferences
import com.example.airecorder.domain.model.StorageStats
import com.example.airecorder.domain.model.SummaryType
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val storageStats: StorageStats = StorageStats(0, 0, 0),
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val meetingRepository: MeetingRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val storage = MutableStateFlow(StorageStats(0, 0, 0))

    init {
        refreshStorage()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferences,
        storage,
        message,
    ) { preferences, storageStats, snackbar ->
        SettingsUiState(preferences = preferences, storageStats = storageStats, message = snackbar)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setAutoTranscribe(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoTranscribe(enabled) }
    }

    fun setAutoSummary(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSummary(enabled) }
    }

    fun setSummaryType(type: SummaryType) {
        viewModelScope.launch { settingsRepository.setSummaryType(type) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setTranscriptionLanguage(language) }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            meetingRepository.deleteAllMeetings()
            refreshStorage()
            message.value = "All local meeting data deleted."
        }
    }

    fun clearMessage() {
        message.value = null
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            storage.value = meetingRepository.getStorageStats()
        }
    }
}
