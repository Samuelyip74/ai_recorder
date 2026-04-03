package com.example.airecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.airecorder.data.local.entity.SummaryEntity

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId")
    suspend fun getByMeetingId(meetingId: Long): SummaryEntity?
}
