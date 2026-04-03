package com.example.airecorder.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    fun play(filePath: String)
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}
