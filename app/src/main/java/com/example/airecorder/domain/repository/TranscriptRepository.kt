package com.example.airecorder.domain.repository

import com.example.airecorder.domain.model.Transcript

interface TranscriptRepository {
    suspend fun upsertProcessing(meetingId: Long, language: String)
    suspend fun saveCompleted(meetingId: Long, text: String, language: String)
    suspend fun markFailed(meetingId: Long, language: String)
    suspend fun updateTranscriptText(meetingId: Long, text: String)
    suspend fun getTranscript(meetingId: Long): Transcript?
}
