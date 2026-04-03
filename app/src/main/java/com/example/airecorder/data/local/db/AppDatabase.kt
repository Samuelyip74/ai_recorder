package com.example.airecorder.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.airecorder.data.local.dao.MeetingDao
import com.example.airecorder.data.local.dao.SummaryDao
import com.example.airecorder.data.local.dao.TranscriptDao
import com.example.airecorder.data.local.entity.MeetingEntity
import com.example.airecorder.data.local.entity.SummaryEntity
import com.example.airecorder.data.local.entity.TranscriptEntity

@Database(
    entities = [MeetingEntity::class, TranscriptEntity::class, SummaryEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun summaryDao(): SummaryDao
}
