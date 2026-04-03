package com.example.airecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.airecorder.domain.model.SummaryStatus
import com.example.airecorder.domain.model.SummaryType

@Entity(
    tableName = "summaries",
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
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val text: String,
    val type: SummaryType,
    val status: SummaryStatus,
    val edited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
