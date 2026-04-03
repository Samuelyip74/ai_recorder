package com.example.airecorder.ui.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.audio.AudioRecorder
import com.example.airecorder.domain.model.RecorderState
import com.example.airecorder.domain.model.RecordingDraft
import com.example.airecorder.domain.usecase.SaveRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecorderUiState(
    val recorderState: RecorderState = RecorderState.IDLE,
    val elapsedMs: Long = 0L,
    val pendingDraft: RecordingDraft? = null,
    val message: String? = null,
)

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val saveRecordingUseCase: SaveRecordingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var startedAt = 0L
    private var elapsedBeforePause = 0L

    fun startRecording() {
        viewModelScope.launch {
            audioRecorder.start()
                .onSuccess {
                    startedAt = System.currentTimeMillis()
                    startTimer()
                    _uiState.update { it.copy(recorderState = RecorderState.RECORDING, pendingDraft = null, message = null) }
                }
                .onFailure {
                    _uiState.update { it.copy(recorderState = RecorderState.ERROR, message = "Unable to start recording.") }
                }
        }
    }

    fun pauseRecording() {
        viewModelScope.launch {
            audioRecorder.pause()
                .onSuccess {
                    elapsedBeforePause = _uiState.value.elapsedMs
                    timerJob?.cancel()
                    _uiState.update { it.copy(recorderState = RecorderState.PAUSED) }
                }
                .onFailure {
                    _uiState.update { it.copy(message = "Unable to pause recording.") }
                }
        }
    }

    fun resumeRecording() {
        viewModelScope.launch {
            audioRecorder.resume()
                .onSuccess {
                    startedAt = System.currentTimeMillis()
                    startTimer()
                    _uiState.update { it.copy(recorderState = RecorderState.RECORDING) }
                }
                .onFailure {
                    _uiState.update { it.copy(message = "Unable to resume recording.") }
                }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            timerJob?.cancel()
            audioRecorder.stop()
                .onSuccess { recorded ->
                    elapsedBeforePause = 0L
                    _uiState.update {
                        it.copy(
                            recorderState = RecorderState.STOPPED_AWAITING_NAME,
                            elapsedMs = recorded.durationMs,
                            pendingDraft = RecordingDraft(
                                tempFilePath = recorded.filePath,
                                durationMs = recorded.durationMs,
                                fileSizeBytes = recorded.fileSizeBytes,
                            ),
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(recorderState = RecorderState.ERROR, message = "Unable to stop recording.") }
                }
        }
    }

    fun saveMeeting(inputName: String) {
        val draft = _uiState.value.pendingDraft ?: return
        val finalName = inputName.ifBlank { "Meeting ${DateFormat.getDateTimeInstance().format(Date())}" }
        viewModelScope.launch {
            _uiState.update { it.copy(recorderState = RecorderState.SAVING) }
            saveRecordingUseCase(
                name = finalName,
                tempFilePath = draft.tempFilePath,
                durationMs = draft.durationMs,
                fileSizeBytes = draft.fileSizeBytes,
            ).onSuccess {
                reset("Meeting saved locally.")
            }.onFailure {
                _uiState.update { it.copy(recorderState = RecorderState.ERROR, message = "Failed to save recording.") }
            }
        }
    }

    fun discardPendingRecording() {
        viewModelScope.launch {
            _uiState.value.pendingDraft?.let { File(it.tempFilePath).delete() }
            reset()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(message = "Microphone permission denied. Recording cannot start.") }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(elapsedMs = elapsedBeforePause + (System.currentTimeMillis() - startedAt)) }
                delay(250)
            }
        }
    }

    private fun reset(message: String? = null) {
        timerJob?.cancel()
        startedAt = 0L
        elapsedBeforePause = 0L
        _uiState.value = RecorderUiState(message = message)
    }
}
