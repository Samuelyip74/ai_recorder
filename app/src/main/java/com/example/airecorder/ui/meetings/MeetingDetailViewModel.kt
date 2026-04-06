package com.example.airecorder.ui.meetings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.audio.AudioPlayer
import com.example.airecorder.domain.model.MeetingDetail
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.domain.repository.SettingsRepository
import com.example.airecorder.domain.repository.TranscriptRepository
import com.example.airecorder.domain.usecase.SaveRecordingUseCase
import com.example.airecorder.domain.usecase.TranslateTranscriptUseCase
import com.example.airecorder.rainbow.RainbowLinkedMeetingResolver
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
    val translatedTranscript: String = "",
    val isPlaying: Boolean = false,
    val isTranslating: Boolean = false,
    val currentPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val isResolvingAudio: Boolean = false,
    val shareAudioPath: String? = null,
    val message: String? = null,
    val translationTargetLanguage: String = "es",
)

private data class PlaybackState(
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val playbackDurationMs: Long,
)

private data class EditorState(
    val transcriptDraft: String?,
    val translatedTranscript: String,
    val isTranslating: Boolean,
    val isResolvingAudio: Boolean,
    val shareAudioPath: String?,
)

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meetingRepository: MeetingRepository,
    private val transcriptRepository: TranscriptRepository,
    private val settingsRepository: SettingsRepository,
    private val saveRecordingUseCase: SaveRecordingUseCase,
    private val translateTranscriptUseCase: TranslateTranscriptUseCase,
    private val rainbowLinkedMeetingResolver: RainbowLinkedMeetingResolver,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    companion object {
        private const val TAG = "MeetingDetailViewModel"
    }

    private val meetingId: Long = checkNotNull(savedStateHandle["meetingId"])
    private val message = MutableStateFlow<String?>(null)
    private val transcriptDraft = MutableStateFlow<String?>(null)
    private val translatedTranscript = MutableStateFlow("")
    private val isTranslating = MutableStateFlow(false)
    private val isResolvingAudio = MutableStateFlow(false)
    private val shareAudioPath = MutableStateFlow<String?>(null)

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
        translatedTranscript,
        isTranslating,
        isResolvingAudio,
        shareAudioPath,
    ) { transcriptText, translatedText, translating, resolvingAudio, pendingSharePath ->
        EditorState(
            transcriptDraft = transcriptText,
            translatedTranscript = translatedText,
            isTranslating = translating,
            isResolvingAudio = resolvingAudio,
            shareAudioPath = pendingSharePath,
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
            translatedTranscript = editor.translatedTranscript,
            isPlaying = playback.isPlaying,
            isTranslating = editor.isTranslating,
            currentPositionMs = playback.currentPositionMs,
            playbackDurationMs = playback.playbackDurationMs,
            isResolvingAudio = editor.isResolvingAudio,
            shareAudioPath = editor.shareAudioPath,
            message = snackbar,
            translationTargetLanguage = preferences.translationTargetLanguage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetingDetailUiState())

    fun togglePlayback() {
        val detail = uiState.value.detail ?: return
        if (uiState.value.isPlaying) {
            audioPlayer.pause()
            return
        }

        viewModelScope.launch {
            isResolvingAudio.value = true
            rainbowLinkedMeetingResolver.resolvePlaybackFile(detail.meeting)
                .onSuccess { audioPlayer.play(it.absolutePath) }
                .onFailure {
                    Log.e(TAG, "Playback preparation failed for meetingId=$meetingId", it)
                    message.value = it.message ?: "Unable to load recording."
                }
            isResolvingAudio.value = false
        }
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

    fun translateTranscript(targetLanguage: String) {
        val normalizedTargetLanguage = targetLanguage.trim().lowercase()
        if (uiState.value.isTranslating || normalizedTargetLanguage.isBlank()) return
        viewModelScope.launch {
            isTranslating.value = true
            try {
                settingsRepository.setTranslationTargetLanguage(normalizedTargetLanguage)
                translateTranscriptUseCase(meetingId, normalizedTargetLanguage)
                    .onSuccess {
                        translatedTranscript.value = it
                        message.value = "Transcript translated locally."
                    }
                    .onFailure {
                        Log.e(TAG, "Transcript translation failed for meetingId=$meetingId", it)
                        message.value = "Translation failed: ${it.message ?: "Unknown error"}"
                    }
            } finally {
                isTranslating.value = false
            }
        }
    }

    fun updateTranscriptDraft(value: String) {
        transcriptDraft.value = value
    }

    fun saveTranscriptEdit() {
        viewModelScope.launch {
            transcriptRepository.updateTranscriptText(meetingId, uiState.value.transcriptDraft)
            message.value = "Transcript updated."
        }
    }

    fun shareRecording() {
        val detail = uiState.value.detail ?: return
        viewModelScope.launch {
            isResolvingAudio.value = true
            rainbowLinkedMeetingResolver.resolvePlaybackFile(detail.meeting)
                .onSuccess { shareAudioPath.value = it.absolutePath }
                .onFailure {
                    Log.e(TAG, "Share preparation failed for meetingId=$meetingId", it)
                    message.value = it.message ?: "Unable to prepare recording for sharing."
                }
            isResolvingAudio.value = false
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun consumeShareAudioPath() {
        shareAudioPath.value = null
    }

    override fun onCleared() {
        audioPlayer.pause()
        super.onCleared()
    }
}
