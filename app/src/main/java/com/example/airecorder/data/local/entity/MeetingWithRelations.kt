package com.example.airecorder.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MeetingWithRelations(
    @Embedded val meeting: MeetingEntity,
    @Relation(parentColumn = "id", entityColumn = "meetingId")
    val transcript: TranscriptEntity?,
    @Relation(parentColumn = "id", entityColumn = "meetingId")
    val summary: SummaryEntity?,
)
