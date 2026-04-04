package com.example.airecorder.domain.repository

import com.example.airecorder.domain.model.Meeting
import com.example.airecorder.domain.model.MeetingDetail
import com.example.airecorder.domain.model.RecordingMode
import com.example.airecorder.domain.model.StorageStats
import kotlinx.coroutines.flow.Flow

interface MeetingRepository {
    fun observeMeetings(searchQuery: String): Flow<List<Meeting>>
    fun observeMeetingDetail(meetingId: Long): Flow<MeetingDetail?>
    suspend fun createMeeting(
        name: String,
        tempFilePath: String,
        tempWhisperFilePath: String,
        durationMs: Long,
        fileSizeBytes: Long,
        recordingMode: RecordingMode,
        captureNotes: String,
    ): Long
    suspend fun renameMeeting(meetingId: Long, newName: String)
    suspend fun deleteMeeting(meetingId: Long)
    suspend fun deleteAllMeetings()
    suspend fun getStorageStats(): StorageStats
}
