package com.example.airecorder.data.repository

import android.content.Context
import com.example.airecorder.data.local.dao.MeetingDao
import com.example.airecorder.data.local.dao.SummaryDao
import com.example.airecorder.data.local.dao.TranscriptDao
import com.example.airecorder.data.local.entity.MeetingEntity
import com.example.airecorder.domain.model.RecordingMode
import com.example.airecorder.domain.model.StorageStats
import com.example.airecorder.domain.model.SummaryStatus
import com.example.airecorder.domain.model.TranscriptStatus
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.util.meetingsDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val meetingDao: MeetingDao,
    private val transcriptDao: TranscriptDao,
    private val summaryDao: SummaryDao,
    @ApplicationContext private val context: Context,
) : MeetingRepository {

    override fun observeMeetings(searchQuery: String): Flow<List<com.example.airecorder.domain.model.Meeting>> {
        return meetingDao.observeMeetings(searchQuery).map { list -> list.map { it.toDomain() } }
    }

    override fun observeMeetingDetail(meetingId: Long): Flow<com.example.airecorder.domain.model.MeetingDetail?> =
        meetingDao.observeMeetingDetail(meetingId).map { it?.toDomain() }

    override suspend fun createMeeting(
        name: String,
        tempFilePath: String,
        durationMs: Long,
        fileSizeBytes: Long,
        recordingMode: RecordingMode,
        captureNotes: String,
    ): Long {
        val now = System.currentTimeMillis()
        val finalFile = moveTempFileToMeetings(tempFilePath, now)
        return meetingDao.insert(
            MeetingEntity(
                name = name,
                createdAt = now,
                updatedAt = now,
                audioFilePath = finalFile.absolutePath,
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes.takeIf { it > 0 } ?: finalFile.length(),
                recordingMode = recordingMode,
                captureNotes = captureNotes,
                transcriptStatus = TranscriptStatus.NOT_STARTED,
                summaryStatus = SummaryStatus.NOT_STARTED,
            ),
        )
    }

    override suspend fun renameMeeting(meetingId: Long, newName: String) {
        val existing = meetingDao.getById(meetingId) ?: return
        meetingDao.update(existing.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteMeeting(meetingId: Long) {
        val existing = meetingDao.getById(meetingId) ?: return
        File(existing.audioFilePath).takeIf { it.exists() }?.delete()
        meetingDao.deleteById(meetingId)
    }

    override suspend fun deleteAllMeetings() {
        val directory = context.meetingsDirectory()
        directory.listFiles()?.forEach { it.delete() }
        meetingDao.deleteAll()
    }

    override suspend fun getStorageStats(): StorageStats {
        val audioBytes = context.meetingsDirectory().listFiles()?.sumOf { it.length() } ?: 0L
        val textBytes = calculateTextBytes(meetingDao.getAll())
        return StorageStats(
            totalBytes = audioBytes + textBytes,
            audioBytes = audioBytes,
            textBytes = textBytes,
        )
    }

    private suspend fun calculateTextBytes(meetings: List<MeetingEntity>): Long {
        var total = 0L
        meetings.forEach { meeting ->
            total += transcriptDao.getByMeetingId(meeting.id)?.text?.toByteArray()?.size?.toLong() ?: 0L
            total += summaryDao.getByMeetingId(meeting.id)?.text?.toByteArray()?.size?.toLong() ?: 0L
        }
        return total
    }

    private fun moveTempFileToMeetings(tempFilePath: String, timestamp: Long): File {
        val source = File(tempFilePath)
        val extension = source.extension.takeIf { it.isNotBlank() } ?: "m4a"
        val finalFile = File(context.meetingsDirectory(), "meeting_$timestamp.$extension")
        if (!source.renameTo(finalFile)) {
            source.copyTo(finalFile, overwrite = true)
            source.delete()
        }
        return finalFile
    }
}
