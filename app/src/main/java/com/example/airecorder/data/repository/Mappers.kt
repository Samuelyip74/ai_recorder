package com.example.airecorder.data.repository

import com.example.airecorder.data.local.entity.MeetingEntity
import com.example.airecorder.data.local.entity.MeetingWithRelations
import com.example.airecorder.data.local.entity.SummaryEntity
import com.example.airecorder.data.local.entity.TranscriptEntity
import com.example.airecorder.domain.model.Meeting
import com.example.airecorder.domain.model.MeetingDetail
import com.example.airecorder.domain.model.Summary
import com.example.airecorder.domain.model.Transcript

fun MeetingEntity.toDomain() = Meeting(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    audioFilePath = audioFilePath,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    recordingMode = recordingMode,
    captureNotes = captureNotes,
    transcriptStatus = transcriptStatus,
    summaryStatus = summaryStatus,
)

fun TranscriptEntity.toDomain() = Transcript(
    id = id,
    meetingId = meetingId,
    text = text,
    language = language,
    timestampsJson = timestampsJson,
    status = status,
    edited = edited,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SummaryEntity.toDomain() = Summary(
    id = id,
    meetingId = meetingId,
    text = text,
    type = type,
    status = status,
    edited = edited,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MeetingWithRelations.toDomain() = MeetingDetail(
    meeting = meeting.toDomain(),
    transcript = transcript?.toDomain(),
    summary = summary?.toDomain(),
)
