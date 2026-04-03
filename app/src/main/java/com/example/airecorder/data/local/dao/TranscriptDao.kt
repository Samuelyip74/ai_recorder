package com.example.airecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.airecorder.data.local.entity.TranscriptEntity

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranscriptEntity)

    @Query("SELECT * FROM transcripts WHERE meetingId = :meetingId")
    suspend fun getByMeetingId(meetingId: Long): TranscriptEntity?
}
