package com.example.airecorder.data.repository

import com.example.airecorder.data.local.dao.MeetingDao
import com.example.airecorder.data.local.dao.SummaryDao
import com.example.airecorder.data.local.entity.SummaryEntity
import com.example.airecorder.domain.model.Summary
import com.example.airecorder.domain.model.SummaryStatus
import com.example.airecorder.domain.model.SummaryType
import com.example.airecorder.domain.repository.SummaryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepositoryImpl @Inject constructor(
    private val summaryDao: SummaryDao,
    private val meetingDao: MeetingDao,
) : SummaryRepository {

    override suspend fun upsertProcessing(meetingId: Long, type: SummaryType) {
        val existing = summaryDao.getByMeetingId(meetingId)
        val now = System.currentTimeMillis()
        summaryDao.upsert(
            SummaryEntity(
                id = existing?.id ?: 0,
                meetingId = meetingId,
                text = existing?.text.orEmpty(),
                type = type,
                status = SummaryStatus.PROCESSING,
                edited = existing?.edited ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        meetingDao.getById(meetingId)?.let {
            meetingDao.update(it.copy(summaryStatus = SummaryStatus.PROCESSING, updatedAt = now))
        }
    }

    override suspend fun saveCompleted(meetingId: Long, text: String, type: SummaryType) {
        val existing = summaryDao.getByMeetingId(meetingId)
        val now = System.currentTimeMillis()
        summaryDao.upsert(
            SummaryEntity(
                id = existing?.id ?: 0,
                meetingId = meetingId,
                text = text,
                type = type,
                status = SummaryStatus.COMPLETED,
                edited = existing?.edited ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        meetingDao.getById(meetingId)?.let {
            meetingDao.update(it.copy(summaryStatus = SummaryStatus.COMPLETED, updatedAt = now))
        }
    }

    override suspend fun markFailed(meetingId: Long, type: SummaryType) {
        val existing = summaryDao.getByMeetingId(meetingId)
        val now = System.currentTimeMillis()
        summaryDao.upsert(
            SummaryEntity(
                id = existing?.id ?: 0,
                meetingId = meetingId,
                text = existing?.text.orEmpty(),
                type = type,
                status = SummaryStatus.FAILED,
                edited = existing?.edited ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        meetingDao.getById(meetingId)?.let {
            meetingDao.update(it.copy(summaryStatus = SummaryStatus.FAILED, updatedAt = now))
        }
    }

    override suspend fun updateSummaryText(meetingId: Long, text: String, type: SummaryType) {
        val existing = summaryDao.getByMeetingId(meetingId) ?: return
        summaryDao.upsert(
            existing.copy(
                text = text,
                type = type,
                edited = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun getSummary(meetingId: Long): Summary? = summaryDao.getByMeetingId(meetingId)?.toDomain()
}
