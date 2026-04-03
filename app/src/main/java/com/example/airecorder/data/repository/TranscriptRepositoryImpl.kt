package com.example.airecorder.data.repository

import com.example.airecorder.data.local.dao.MeetingDao
import com.example.airecorder.data.local.dao.TranscriptDao
import com.example.airecorder.data.local.entity.TranscriptEntity
import com.example.airecorder.domain.model.Transcript
import com.example.airecorder.domain.model.TranscriptStatus
import com.example.airecorder.domain.repository.TranscriptRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptRepositoryImpl @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val meetingDao: MeetingDao,
) : TranscriptRepository {

    override suspend fun upsertProcessing(meetingId: Long, language: String) {
        val existing = transcriptDao.getByMeetingId(meetingId)
        val now = System.currentTimeMillis()
        transcriptDao.upsert(
            TranscriptEntity(
                id = existing?.id ?: 0,
                meetingId = meetingId,
                text = existing?.text.orEmpty(),
                language = language,
                timestampsJson = existing?.timestampsJson,
                status = TranscriptStatus.PROCESSING,
                edited = existing?.edited ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        meetingDao.getById(meetingId)?.let {
            meetingDao.update(it.copy(transcriptStatus = TranscriptStatus.PROCESSING, updatedAt = now))
        }
    }

    override suspend fun saveCompleted(meetingId: Long, text: String, language: String) {
        val existing = transcriptDao.getByMeetingId(meetingId)
        val now = System.currentTimeMillis()
        transcriptDao.upsert(
            TranscriptEntity(
                id = existing?.id ?: 0,
                meetingId = meetingId,
                text = text,
                language = language,
                timestampsJson = existing?.timestampsJson,
                status = TranscriptStatus.COMPLETED,
                edited = existing?.edited ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        meetingDao.getById(meetingId)?.let {
            meetingDao.update(it.copy(transcriptStatus = TranscriptStatus.COMPLETED, updatedAt = now))
        }
    }

    override suspend fun markFailed(meetingId: Long, language: String) {
        val existing = transcriptDao.getByMeetingId(meetingId)
        val now = System.currentTimeMillis()
        transcriptDao.upsert(
            TranscriptEntity(
                id = existing?.id ?: 0,
                meetingId = meetingId,
                text = existing?.text.orEmpty(),
                language = language,
                timestampsJson = existing?.timestampsJson,
                status = TranscriptStatus.FAILED,
                edited = existing?.edited ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        meetingDao.getById(meetingId)?.let {
            meetingDao.update(it.copy(transcriptStatus = TranscriptStatus.FAILED, updatedAt = now))
        }
    }

    override suspend fun updateTranscriptText(meetingId: Long, text: String) {
        val existing = transcriptDao.getByMeetingId(meetingId) ?: return
        val now = System.currentTimeMillis()
        transcriptDao.upsert(existing.copy(text = text, edited = true, updatedAt = now))
    }

    override suspend fun getTranscript(meetingId: Long): Transcript? = transcriptDao.getByMeetingId(meetingId)?.toDomain()
}
