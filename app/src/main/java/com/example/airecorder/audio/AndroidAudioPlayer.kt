package com.example.airecorder.audio

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AndroidAudioPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : AudioPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(context).build()
    private val _isPlaying = MutableStateFlow(false)
    private val _currentPositionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)

    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    init {
        scope.launch {
            while (true) {
                _isPlaying.value = player.isPlaying
                _currentPositionMs.value = player.currentPosition
                _durationMs.value = if (player.duration > 0) player.duration else 0L
                delay(250)
            }
        }
    }

    override fun play(filePath: String) {
        if (player.currentMediaItem?.localConfiguration?.uri?.path != filePath) {
            player.setMediaItem(MediaItem.fromUri(filePath.toUri()))
            player.prepare()
        }
        player.playWhenReady = true
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun release() {
        player.release()
    }
}
