package com.example.airecorder.ui.meetings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.audio.AudioPlayer
import com.example.airecorder.domain.model.MeetingDetail
import com.example.airecorder.domain.model.SummaryType
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.domain.repository.SettingsRepository
import com.example.airecorder.domain.repository.SummaryRepository
import com.example.airecorder.domain.repository.TranscriptRepository
import com.example.airecorder.domain.usecase.SaveRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeetingDetailUiState(
    val detail: MeetingDetail? = null,
    val transcriptDraft: String = "",
    val summaryDraft: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val message: String? = null,
    val summaryType: SummaryType = SummaryType.CONCISE,
)

private data class PlaybackState(
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val playbackDurationMs: Long,
)

private data class EditorState(
    val transcriptDraft: String?,
    val summaryDraft: String?,
)

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meetingRepository: MeetingRepository,
    private val transcriptRepository: TranscriptRepository,
    private val summaryRepository: SummaryRepository,
    private val settingsRepository: SettingsRepository,
    private val saveRecordingUseCase: SaveRecordingUseCase,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    companion object {
        private const val TAG = "MeetingDetailViewModel"
    }

    private val meetingId: Long = checkNotNull(savedStateHandle["meetingId"])
    private val message = MutableStateFlow<String?>(null)
    private val transcriptDraft = MutableStateFlow<String?>(null)
    private val summaryDraft = MutableStateFlow<String?>(null)

    private val playbackState = combine(
        audioPlayer.isPlaying,
        audioPlayer.currentPositionMs,
        audioPlayer.durationMs,
    ) { isPlaying, position, duration ->
        PlaybackState(
            isPlaying = isPlaying,
            currentPositionMs = position,
            playbackDurationMs = duration,
        )
    }

    private val editorState = combine(
        transcriptDraft,
        summaryDraft,
    ) { transcriptText, summaryText ->
        EditorState(
            transcriptDraft = transcriptText,
            summaryDraft = summaryText,
        )
    }

    val uiState: StateFlow<MeetingDetailUiState> = combine(
        meetingRepository.observeMeetingDetail(meetingId),
        playbackState,
        settingsRepository.preferences,
        message,
        editorState,
    ) { detail, playback, preferences, snackbar, editor ->
        MeetingDetailUiState(
            detail = detail,
            transcriptDraft = editor.transcriptDraft ?: detail?.transcript?.text.orEmpty(),
            summaryDraft = editor.summaryDraft ?: detail?.summary?.text.orEmpty(),
            isPlaying = playback.isPlaying,
            currentPositionMs = playback.currentPositionMs,
            playbackDurationMs = playback.playbackDurationMs,
            message = snackbar,
            summaryType = preferences.summaryType,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetingDetailUiState())

    fun togglePlayback() {
        val detail = uiState.value.detail ?: return
        if (uiState.value.isPlaying) audioPlayer.pause() else audioPlayer.play(detail.meeting.audioFilePath)
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun renameMeeting(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            meetingRepository.renameMeeting(meetingId, name)
            message.value = "Meeting renamed."
        }
    }

    fun deleteMeeting() {
        viewModelScope.launch {
            audioPlayer.pause()
            meetingRepository.deleteMeeting(meetingId)
        }
    }

    fun generateTranscript() {
        viewModelScope.launch {
            val language = settingsRepository.preferences.first().transcriptionLanguage
            saveRecordingUseCase.generateTranscript(meetingId, language)
                .onSuccess { message.value = "Transcript generated locally." }
                .onFailure {
                    Log.e(TAG, "Transcript generation failed for meetingId=$meetingId", it)
                    message.value = "Transcript generation failed: ${it.message ?: "Unknown error"}"
                }
        }
    }

    fun generateSummary() {
        viewModelScope.launch {
            saveRecordingUseCase.generateSummary(meetingId, uiState.value.summaryType)
                .onSuccess { message.value = "Summary generated locally." }
                .onFailure { message.value = "Summary generation failed." }
        }
    }

    fun updateTranscriptDraft(value: String) {
        transcriptDraft.value = value
    }

    fun updateSummaryDraft(value: String) {
        summaryDraft.value = value
    }

    fun saveTranscriptEdit() {
        viewModelScope.launch {
            transcriptRepository.updateTranscriptText(meetingId, uiState.value.transcriptDraft)
            message.value = "Transcript updated."
        }
    }

    fun saveSummaryEdit() {
        viewModelScope.launch {
            summaryRepository.updateSummaryText(meetingId, uiState.value.summaryDraft, uiState.value.summaryType)
            message.value = "Summary updated."
        }
    }

    fun clearMessage() {
        message.value = null
    }

    override fun onCleared() {
        audioPlayer.pause()
        super.onCleared()
    }
}
