package com.example.airecorder.ui.meetings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.domain.model.Meeting
import com.example.airecorder.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class MeetingsUiState(
    val searchQuery: String = "",
    val meetings: List<Meeting> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MeetingsViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val meetings = searchQuery
        .flatMapLatest { query -> meetingRepository.observeMeetings(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<MeetingsUiState> = combine(searchQuery, meetings) { query, meetingList ->
        MeetingsUiState(searchQuery = query, meetings = meetingList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetingsUiState())

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }
}
