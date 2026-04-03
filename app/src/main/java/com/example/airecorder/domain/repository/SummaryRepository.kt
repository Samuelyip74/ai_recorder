package com.example.airecorder.domain.repository

import com.example.airecorder.domain.model.Summary
import com.example.airecorder.domain.model.SummaryType

interface SummaryRepository {
    suspend fun upsertProcessing(meetingId: Long, type: SummaryType)
    suspend fun saveCompleted(meetingId: Long, text: String, type: SummaryType)
    suspend fun markFailed(meetingId: Long, type: SummaryType)
    suspend fun updateSummaryText(meetingId: Long, text: String, type: SummaryType)
    suspend fun getSummary(meetingId: Long): Summary?
}
