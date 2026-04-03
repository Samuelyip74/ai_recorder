package com.example.airecorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.airecorder.domain.model.SummaryStatus
import com.example.airecorder.domain.model.TranscriptStatus

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val transcriptStatus: TranscriptStatus,
    val summaryStatus: SummaryStatus,
)
