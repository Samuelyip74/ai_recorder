package com.example.airecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.airecorder.data.local.entity.MeetingEntity
import com.example.airecorder.data.local.entity.MeetingWithRelations
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query(
        """
        SELECT DISTINCT m.* FROM meetings m
        LEFT JOIN transcripts t ON t.meetingId = m.id
        LEFT JOIN summaries s ON s.meetingId = m.id
        WHERE :query = ''
           OR LOWER(m.name) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(COALESCE(t.text, '')) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(COALESCE(s.text, '')) LIKE '%' || LOWER(:query) || '%'
        ORDER BY m.createdAt DESC
        """,
    )
    fun observeMeetings(query: String): Flow<List<MeetingEntity>>

    @Transaction
    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    fun observeMeetingDetail(meetingId: Long): Flow<MeetingWithRelations?>

    @Insert
    suspend fun insert(meeting: MeetingEntity): Long

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Delete
    suspend fun delete(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    suspend fun getById(meetingId: Long): MeetingEntity?

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    suspend fun deleteById(meetingId: Long)

    @Query("DELETE FROM meetings")
    suspend fun deleteAll()

    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    suspend fun getAll(): List<MeetingEntity>
}
