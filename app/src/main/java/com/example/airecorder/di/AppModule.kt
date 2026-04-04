package com.example.airecorder.di

import android.content.Context
import androidx.room.Room
import com.example.airecorder.audio.AndroidAudioPlayer
import com.example.airecorder.audio.AudioPlayer
import com.example.airecorder.audio.AudioRecorder
import com.example.airecorder.audio.DefaultAudioRecorder
import com.example.airecorder.data.local.dao.MeetingDao
import com.example.airecorder.data.local.dao.SummaryDao
import com.example.airecorder.data.local.dao.TranscriptDao
import com.example.airecorder.data.local.db.AppDatabase
import com.example.airecorder.data.repository.MeetingRepositoryImpl
import com.example.airecorder.data.repository.SettingsRepositoryImpl
import com.example.airecorder.data.repository.SummaryRepositoryImpl
import com.example.airecorder.data.repository.TranscriptRepositoryImpl
import com.example.airecorder.domain.repository.MeetingRepository
import com.example.airecorder.domain.repository.SettingsRepository
import com.example.airecorder.domain.repository.SummaryRepository
import com.example.airecorder.domain.repository.TranscriptRepository
import com.example.airecorder.summary.PlaceholderSummaryGenerator
import com.example.airecorder.summary.SummaryGenerator
import com.example.airecorder.transcription.TranscriptGenerator
import com.example.airecorder.transcription.WhisperTranscriptGenerator
import com.example.airecorder.translation.MlKitTextTranslator
import com.example.airecorder.translation.TextTranslator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun bindAudioRecorder(impl: DefaultAudioRecorder): AudioRecorder
    @Binds abstract fun bindAudioPlayer(impl: AndroidAudioPlayer): AudioPlayer
    @Binds abstract fun bindMeetingRepository(impl: MeetingRepositoryImpl): MeetingRepository
    @Binds abstract fun bindTranscriptRepository(impl: TranscriptRepositoryImpl): TranscriptRepository
    @Binds abstract fun bindSummaryRepository(impl: SummaryRepositoryImpl): SummaryRepository
    @Binds abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun bindTranscriptGenerator(impl: WhisperTranscriptGenerator): TranscriptGenerator
    @Binds abstract fun bindSummaryGenerator(impl: PlaceholderSummaryGenerator): SummaryGenerator
    @Binds abstract fun bindTextTranslator(impl: MlKitTextTranslator): TextTranslator
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "ai_recorder.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideMeetingDao(db: AppDatabase): MeetingDao = db.meetingDao()
    @Provides fun provideTranscriptDao(db: AppDatabase): TranscriptDao = db.transcriptDao()
    @Provides fun provideSummaryDao(db: AppDatabase): SummaryDao = db.summaryDao()
}
