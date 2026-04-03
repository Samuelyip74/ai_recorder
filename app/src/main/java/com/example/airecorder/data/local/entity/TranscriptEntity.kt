package com.example.airecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.airecorder.domain.model.TranscriptStatus

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("meetingId", unique = true)],
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val text: String,
    val language: String,
    val timestampsJson: String?,
    val status: TranscriptStatus,
    val edited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
