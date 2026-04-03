package com.example.airecorder.summary

import com.example.airecorder.domain.model.SummaryType

interface SummaryGenerator {
    suspend fun generate(transcript: String, type: SummaryType): Result<String>
}
