package com.example.airecorder.data.local.db

import androidx.room.TypeConverter
import com.example.airecorder.domain.model.SummaryStatus
import com.example.airecorder.domain.model.SummaryType
import com.example.airecorder.domain.model.TranscriptStatus

class Converters {
    @TypeConverter
    fun fromTranscriptStatus(value: TranscriptStatus): String = value.name

    @TypeConverter
    fun toTranscriptStatus(value: String): TranscriptStatus = TranscriptStatus.valueOf(value)

    @TypeConverter
    fun fromSummaryStatus(value: SummaryStatus): String = value.name

    @TypeConverter
    fun toSummaryStatus(value: String): SummaryStatus = SummaryStatus.valueOf(value)

    @TypeConverter
    fun fromSummaryType(value: SummaryType): String = value.name

    @TypeConverter
    fun toSummaryType(value: String): SummaryType = SummaryType.valueOf(value)
}
