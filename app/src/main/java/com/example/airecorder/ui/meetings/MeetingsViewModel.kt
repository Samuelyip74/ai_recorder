package com.example.airecorder.ui.meetings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.domain.model.Meeting
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.rainbow.RainbowBubbleConversation
import com.example.airecorder.rainbow.RainbowBubbleRepository
import com.example.airecorder.rainbow.RainbowRecordingImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeetingsUiState(
    val searchQuery: String = "",
    val meetings: List<Meeting> = emptyList(),
    val rainbowBubbles: List<RainbowBubbleConversation> = emptyList(),
    val isLoadingRainbowBubbles: Boolean = false,
    val importingRainbowBubbleId: String? = null,
    val rainbowErrorMessage: String? = null,
    val importedMeetingId: Long? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MeetingsViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val rainbowBubbleRepository: RainbowBubbleRepository,
    private val rainbowRecordingImportUseCase: RainbowRecordingImportUseCase,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isLoadingRainbowBubbles = MutableStateFlow(false)
    private val importingRainbowBubbleId = MutableStateFlow<String?>(null)
    private val rainbowErrorMessage = MutableStateFlow<String?>(null)
    private val importedMeetingId = MutableStateFlow<Long?>(null)

    private val meetings = searchQuery
        .flatMapLatest { query -> meetingRepository.observeMeetings(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rainbowBubbles = rainbowBubbleRepository.recordedRooms

    val uiState: StateFlow<MeetingsUiState> = combine(
        searchQuery,
        meetings,
        rainbowBubbles,
        isLoadingRainbowBubbles,
        importingRainbowBubbleId,
        rainbowErrorMessage,
        importedMeetingId,
    ) { values ->
        val query = values[0] as String
        val meetingList = values[1] as List<Meeting>
        val bubbleList = values[2] as List<RainbowBubbleConversation>
        val isLoading = values[3] as Boolean
        val importingBubbleId = values[4] as String?
        val errorMessage = values[5] as String?
        val importedId = values[6] as Long?
        MeetingsUiState(
            searchQuery = query,
            meetings = meetingList,
            rainbowBubbles = bubbleList,
            isLoadingRainbowBubbles = isLoading,
            importingRainbowBubbleId = importingBubbleId,
            rainbowErrorMessage = errorMessage,
            importedMeetingId = importedId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetingsUiState())

    init {
        refreshRainbowBubbles()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun deleteMeeting(meetingId: Long) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(meetingId)
        }
    }

    fun deleteAllMeetings() {
        viewModelScope.launch {
            meetingRepository.deleteAllMeetings()
        }
    }

    fun refreshRainbowBubbles() {
        viewModelScope.launch {
            isLoadingRainbowBubbles.value = true
            rainbowErrorMessage.value = null
            runCatching {
                rainbowBubbleRepository.registerListenerIfNeeded()
                rainbowBubbleRepository.refreshRecordedRooms()
            }.onFailure {
                rainbowErrorMessage.value = it.message ?: "Unable to load Rainbow conference recordings."
            }
            isLoadingRainbowBubbles.value = false
        }
    }

    fun importRainbowRecording(bubble: RainbowBubbleConversation) {
        if (importingRainbowBubbleId.value != null) return
        viewModelScope.launch {
            importingRainbowBubbleId.value = bubble.id
            rainbowErrorMessage.value = null
            importedMeetingId.value = null
            rainbowRecordingImportUseCase(bubble)
                .onSuccess { meetingId ->
                    importedMeetingId.value = meetingId
                }
                .onFailure { throwable ->
                    rainbowErrorMessage.value = throwable.message ?: "Unable to import Rainbow recording."
                }
            importingRainbowBubbleId.value = null
        }
    }

    fun consumeImportedMeetingNavigation() {
        importedMeetingId.value = null
    }
}
