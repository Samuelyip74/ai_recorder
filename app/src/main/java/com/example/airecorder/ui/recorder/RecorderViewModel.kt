package com.example.airecorder.ui.recorder

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.audio.AudioRecorder
import com.example.airecorder.audio.RecordingSupport
import com.example.airecorder.domain.model.RecordingMode
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
import java.util.concurrent.atomic.AtomicLong

data class RecorderUiState(
    val recorderState: RecorderState = RecorderState.IDLE,
    val elapsedMs: Long = 0L,
    val pendingDraft: RecordingDraft? = null,
    val selectedMode: RecordingMode = RecordingMode.MIC,
    val support: RecordingSupport = RecordingSupport(isSupported = true, requiresMicrophonePermission = true),
    val isStarting: Boolean = false,
    val isAwaitingPlaybackConsent: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val saveRecordingUseCase: SaveRecordingUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "RecorderViewModel"
        private val playbackLaunchIds = AtomicLong(0L)
    }

    private val _uiState = MutableStateFlow(
        RecorderUiState(
            support = audioRecorder.getSupport(RecordingMode.MIC),
        ),
    )
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var startedAt = 0L
    private var elapsedBeforePause = 0L
    private var activePlaybackLaunchId: Long? = null

    fun selectRecordingMode(mode: RecordingMode) {
        if (_uiState.value.recorderState == RecorderState.RECORDING || _uiState.value.recorderState == RecorderState.PAUSED) return
        _uiState.update {
            it.copy(
                selectedMode = mode,
                support = audioRecorder.getSupport(mode),
                message = null,
            )
        }
    }

    fun startRecording() {
        if (_uiState.value.isStarting) return
        viewModelScope.launch {
            val mode = _uiState.value.selectedMode
            _uiState.update { it.copy(isStarting = true) }
            Log.d(
                TAG,
                "startRecording launchId=${activePlaybackLaunchId ?: -1} mode=$mode support=${_uiState.value.support}",
            )
            audioRecorder.start(mode)
                .onSuccess {
                    startedAt = System.currentTimeMillis()
                    startTimer()
                    Log.d(TAG, "startRecording success launchId=${activePlaybackLaunchId ?: -1} mode=$mode")
                    _uiState.update {
                        it.copy(
                            recorderState = RecorderState.RECORDING,
                            pendingDraft = null,
                            isStarting = false,
                            isAwaitingPlaybackConsent = false,
                            message = null,
                        )
                    }
                    activePlaybackLaunchId = null
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Unable to start recording for launchId=${activePlaybackLaunchId ?: -1} mode=$mode", throwable)
                    _uiState.update {
                        it.copy(
                            recorderState = RecorderState.ERROR,
                            isStarting = false,
                            isAwaitingPlaybackConsent = false,
                            message = throwable.message ?: "Unable to start recording.",
                        )
                    }
                    activePlaybackLaunchId = null
                }
        }
    }

    fun beginPlaybackConsentRequest(): Long {
        if (_uiState.value.isAwaitingPlaybackConsent || _uiState.value.isStarting) {
            return activePlaybackLaunchId ?: -1L
        }
        val launchId = playbackLaunchIds.incrementAndGet()
        activePlaybackLaunchId = launchId
        Log.d(TAG, "beginPlaybackConsentRequest launchId=$launchId")
        _uiState.update { it.copy(isAwaitingPlaybackConsent = true, message = null) }
        return launchId
    }

    fun setPlaybackCaptureConsent(resultCode: Int, data: Intent?) {
        if (_uiState.value.isStarting) return
        viewModelScope.launch {
            Log.d(
                TAG,
                "setPlaybackCaptureConsent launchId=${activePlaybackLaunchId ?: -1} resultCode=$resultCode hasData=${data != null}",
            )
            audioRecorder.setPlaybackCaptureConsent(resultCode, data)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            support = audioRecorder.getSupport(it.selectedMode),
                            isAwaitingPlaybackConsent = false,
                            message = "Playback capture permission granted.",
                        )
                    }
                    startRecording()
                }
                .onFailure { throwable ->
                    Log.e(
                        TAG,
                        "setPlaybackCaptureConsent failed launchId=${activePlaybackLaunchId ?: -1}",
                        throwable,
                    )
                    _uiState.update { state ->
                        state.copy(
                            isAwaitingPlaybackConsent = false,
                            message = throwable.message ?: "Playback capture permission denied.",
                        )
                    }
                    activePlaybackLaunchId = null
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
                                tempWhisperFilePath = recorded.whisperFilePath,
                                durationMs = recorded.durationMs,
                                fileSizeBytes = recorded.fileSizeBytes,
                                recordingMode = recorded.recordingMode,
                                captureNotes = recorded.captureNotes,
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
        val finalName = inputName.ifBlank { "Recording ${DateFormat.getDateTimeInstance().format(Date())}" }
        viewModelScope.launch {
            _uiState.update { it.copy(recorderState = RecorderState.SAVING) }
            saveRecordingUseCase(
                name = finalName,
                tempFilePath = draft.tempFilePath,
                tempWhisperFilePath = draft.tempWhisperFilePath,
                durationMs = draft.durationMs,
                fileSizeBytes = draft.fileSizeBytes,
                recordingMode = draft.recordingMode,
                captureNotes = draft.captureNotes,
            ).onSuccess {
                reset("Recording saved locally.")
            }.onFailure {
                _uiState.update { it.copy(recorderState = RecorderState.ERROR, message = "Failed to save recording.") }
            }
        }
    }

    fun discardPendingRecording() {
        viewModelScope.launch {
            audioRecorder.cancel()
            _uiState.value.pendingDraft?.let { File(it.tempFilePath).delete() }
            _uiState.value.pendingDraft?.let { draft ->
                if (draft.tempWhisperFilePath != draft.tempFilePath) {
                    File(draft.tempWhisperFilePath).delete()
                }
            }
            reset()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onPermissionDenied() {
        Log.d(TAG, "onPermissionDenied launchId=${activePlaybackLaunchId ?: -1}")
        _uiState.update {
            it.copy(
                isAwaitingPlaybackConsent = false,
                message = "Microphone permission denied. Recording cannot start.",
            )
        }
        activePlaybackLaunchId = null
    }

    fun onProjectionConsentDenied() {
        Log.d(TAG, "onProjectionConsentDenied launchId=${activePlaybackLaunchId ?: -1}")
        _uiState.update {
            it.copy(
                isAwaitingPlaybackConsent = false,
                message = "Playback capture permission denied. Device audio cannot be recorded.",
            )
        }
        activePlaybackLaunchId = null
    }

    fun onProjectionLaunchUnavailable() {
        Log.e(TAG, "MediaProjectionManager unavailable launchId=${activePlaybackLaunchId ?: -1}.")
        _uiState.update {
            it.copy(
                isAwaitingPlaybackConsent = false,
                message = "Playback capture is unavailable on this device right now.",
            )
        }
        activePlaybackLaunchId = null
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
        _uiState.value = RecorderUiState(
            selectedMode = _uiState.value.selectedMode,
            support = audioRecorder.getSupport(_uiState.value.selectedMode),
            message = message,
        )
    }
}
