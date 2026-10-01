package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.equalizer.EqualizerManager
import com.example.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val totalDurationMs: Long = 0,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingSeconds: Int = 0,
    val isSleepTimerActive: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isHiResAudioActive: Boolean = true
)

class MusicPlayerController(
    private val context: Context,
    val equalizerManager: EqualizerManager
) {
    private val TAG = "MusicPlayerController"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaPlayer: MediaPlayer? = null
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    init {
        startProgressLoop()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            val pos = player.currentPosition.toLong()
                            val dur = player.duration.toLong().coerceAtLeast(0L)
                            _uiState.update {
                                it.copy(
                                    currentPositionMs = pos,
                                    totalDurationMs = if (dur > 0) dur else it.totalDurationMs
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
                delay(60) // High frequency for smooth synced lyrics scroll
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        _uiState.update {
            it.copy(
                queue = songs,
                currentIndex = validIndex
            )
        }
        playSong(songs[validIndex])
    }

    fun playSong(song: Song) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                val file = File(song.filePath)
                if (file.exists()) {
                    setDataSource(context, Uri.fromFile(file))
                } else {
                    setDataSource(song.filePath)
                }

                setOnPreparedListener { mp ->
                    mp.start()
                    // Restore custom playback speed if set
                    val currentSpeed = _uiState.value.playbackSpeed
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && currentSpeed != 1.0f) {
                        try {
                            val params = mp.playbackParams
                            params.speed = currentSpeed
                            mp.playbackParams = params
                        } catch (e: Exception) {
                            Log.w(TAG, "Notice setting playbackParams speed: ${e.message}")
                        }
                    }
                    val audioSession = mp.audioSessionId
                    equalizerManager.attachToSession(audioSession)
                    _uiState.update {
                        it.copy(
                            currentSong = song,
                            isPlaying = true,
                            totalDurationMs = mp.duration.toLong().coerceAtLeast(song.durationMs),
                            currentPositionMs = 0,
                            isHiResAudioActive = song.isHiRes
                        )
                    }
                }

                setOnCompletionListener {
                    onSongCompleted()
                }

                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    false
                }

                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error playing song: ${e.message}", e)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: run {
            val song = _uiState.value.currentSong ?: _uiState.value.queue.firstOrNull()
            if (song != null) playSong(song)
            return
        }

        try {
            if (player.isPlaying) {
                player.pause()
                _uiState.update { it.copy(isPlaying = false) }
            } else {
                player.start()
                _uiState.update { it.copy(isPlaying = true) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Toggle play/pause error: ${e.message}")
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _uiState.update { it.copy(currentPositionMs = positionMs) }
        } catch (e: Exception) {
            Log.w(TAG, "Seek error: ${e.message}")
        }
    }

    fun playNext() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        var nextIndex = if (state.isShuffle) {
            (state.queue.indices - state.currentIndex).randomOrNull() ?: 0
        } else {
            state.currentIndex + 1
        }

        if (nextIndex >= state.queue.size) {
            if (state.repeatMode == RepeatMode.ALL) {
                nextIndex = 0
            } else {
                return
            }
        }

        _uiState.update { it.copy(currentIndex = nextIndex) }
        playSong(state.queue[nextIndex])
    }

    fun playPrevious() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        // If played more than 3 seconds, replay current song
        if (state.currentPositionMs > 3000) {
            seekTo(0)
            return
        }

        var prevIndex = state.currentIndex - 1
        if (prevIndex < 0) {
            prevIndex = if (state.repeatMode == RepeatMode.ALL) state.queue.size - 1 else 0
        }

        _uiState.update { it.copy(currentIndex = prevIndex) }
        playSong(state.queue[prevIndex])
    }

    private fun onSongCompleted() {
        val state = _uiState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0)
                mediaPlayer?.start()
                _uiState.update { it.copy(isPlaying = true, currentPositionMs = 0) }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                if (state.currentIndex < state.queue.size - 1) {
                    playNext()
                } else {
                    _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
                }
            }
        }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.update { it.copy(repeatMode = nextMode) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer?.let {
                    val params = it.playbackParams
                    params.speed = speed
                    it.playbackParams = params
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting playback speed: ${e.message}")
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(isSleepTimerActive = false, sleepTimerRemainingSeconds = 0) }
            return
        }

        val totalSeconds = minutes * 60
        _uiState.update { it.copy(isSleepTimerActive = true, sleepTimerRemainingSeconds = totalSeconds) }

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
            }
            if (remaining <= 0) {
                // Fade out volume and pause
                try {
                    mediaPlayer?.pause()
                    _uiState.update { it.copy(isPlaying = false, isSleepTimerActive = false) }
                } catch (e: Exception) {
                    Log.w(TAG, "Error pausing on sleep timer: ${e.message}")
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(isSleepTimerActive = false, sleepTimerRemainingSeconds = 0) }
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        equalizerManager.releaseEffects()
    }
}
