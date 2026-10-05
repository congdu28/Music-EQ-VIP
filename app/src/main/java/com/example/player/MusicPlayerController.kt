package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
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
    val isHiResAudioActive: Boolean = true,
    val isCrossfadeEnabled: Boolean = true,
    val crossfadeDurationSeconds: Float = 2.0f,
    val isLoadingOnlineStream: Boolean = false
)


class MusicPlayerController(
    private val context: Context,
    val equalizerManager: EqualizerManager
) {
    companion object {
        var activeInstance: MusicPlayerController? = null
            private set
    }

    private val TAG = "MusicPlayerController"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaPlayer: MediaPlayer? = null
    private var preparingPlayer: MediaPlayer? = null
    private var crossfadeJob: Job? = null
    private var resolveStreamJob: Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var isHighPrecisionTracking: Boolean = true

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var isDucked = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = _uiState.value.isPlaying
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDucked = true
                try { mediaPlayer?.setVolume(0.2f, 0.2f) } catch (e: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDucked) {
                    isDucked = false
                    try { mediaPlayer?.setVolume(1.0f, 1.0f) } catch (e: Exception) {}
                } else if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resumePlayback()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun setHighPrecisionTracking(enabled: Boolean) {
        isHighPrecisionTracking = enabled
    }

    init {
        activeInstance = this
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
                // Ultra-efficient adaptive polling:
                // 150ms is visually imperceptible to human eye for lyrics & seekbars,
                // while consuming ~60% less CPU & battery than 60ms tight loops.
                // 500ms when in library or background tabs to keep CPU cold.
                val intervalMs = if (_uiState.value.isPlaying) {
                    if (isHighPrecisionTracking) 150L else 500L
                } else {
                    1000L
                }
                delay(intervalMs)
            }
        }
    }

    fun loadInitialQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        val initialSong = songs[validIndex]
        _uiState.update {
            it.copy(
                queue = songs,
                currentIndex = validIndex,
                currentSong = initialSong,
                isPlaying = false,
                currentPositionMs = 0,
                totalDurationMs = initialSong.durationMs,
                isHiResAudioActive = initialSong.isHiRes
            )
        }
    }

    fun updateQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        _uiState.update { current ->
            val curSong = current.currentSong
            val newIndex = if (curSong != null) {
                songs.indexOfFirst { it.id == curSong.id }.let { if (it >= 0) it else 0 }
            } else {
                0
            }
            val updatedCurSong = if (curSong != null) {
                songs.find { it.id == curSong.id } ?: curSong
            } else {
                songs.firstOrNull()
            }
            current.copy(
                queue = songs,
                currentIndex = newIndex,
                currentSong = updatedCurSong
            )
        }
    }

    fun playAllSequential(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        _uiState.update {
            it.copy(
                queue = songs,
                currentIndex = validIndex,
                isShuffle = false
            )
        }
        playSong(songs[validIndex])
    }

    fun playAllShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shuffledList = songs.shuffled()
        _uiState.update {
            it.copy(
                queue = shuffledList,
                currentIndex = 0,
                isShuffle = true
            )
        }
        playSong(shuffledList[0])
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

    @Volatile
    private var currentPlaybackSessionId: Long = 0L

    /**
     * Completely stops, cleans up, and releases all active and preparing media players and coroutine jobs.
     * Prevents dual playback, overlapping audio, or memory leaks.
     */
    fun stopAllPlayback() {
        resolveStreamJob?.cancel()
        resolveStreamJob = null

        crossfadeJob?.cancel()
        crossfadeJob = null

        preparingPlayer?.let { player ->
            try { player.setOnPreparedListener(null) } catch (t: Throwable) {}
            try { player.setOnCompletionListener(null) } catch (t: Throwable) {}
            try { player.setOnErrorListener(null) } catch (t: Throwable) {}
            try { player.reset() } catch (t: Throwable) {}
            try { player.release() } catch (t: Throwable) {}
        }
        preparingPlayer = null

        mediaPlayer?.let { player ->
            try { player.setOnPreparedListener(null) } catch (t: Throwable) {}
            try { player.setOnCompletionListener(null) } catch (t: Throwable) {}
            try { player.setOnErrorListener(null) } catch (t: Throwable) {}
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (t: Throwable) {}
            try { player.reset() } catch (t: Throwable) {}
            try { player.release() } catch (t: Throwable) {}
        }
        mediaPlayer = null
    }

    fun playSong(song: Song) {
        val playbackRequestedAtNanos = System.nanoTime()
        val sessionId = System.currentTimeMillis()
        currentPlaybackSessionId = sessionId

        requestAudioFocus()

        // Handle YouTube online tracks that need stream URL resolution
        if (song.filePath.startsWith("yt://") || (song.format.contains("YouTube") && !song.filePath.startsWith("http"))) {
            val videoId = song.filePath.removePrefix("yt://").trim()

            // IMMEDIATELY stop any existing playback (including library music)
            // so audio never overlaps while the online stream is being fetched!
            stopAllPlayback()

            _uiState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = false,
                    isLoadingOnlineStream = true,
                    currentPositionMs = 0
                )
            }
            MusicPlaybackService.startOrUpdate(context, song, false)

            resolveStreamJob = scope.launch {
                val streamUrl = withContext(Dispatchers.IO) {
                    try {
                        com.example.data.YouTubeMusicService.resolveStreamUrl(videoId)
                    } catch (t: Throwable) {
                        null
                    }
                }
                if (isActive && currentPlaybackSessionId == sessionId) {
                    if (streamUrl != null) {
                        // Lyrics or metadata can finish loading while the stream
                        // URL is resolving. Base the resolved queue item on the
                        // latest version so replacing yt:// does not discard it.
                        val latestSong = _uiState.value.queue.firstOrNull { it.filePath == song.filePath }
                            ?: _uiState.value.currentSong?.takeIf {
                                it.id == song.id && it.title == song.title && it.artist == song.artist
                            }
                            ?: song
                        val resolvedSong = latestSong.copy(filePath = streamUrl)
                        _uiState.update { current ->
                            // YouTube song IDs are derived from video IDs and can collide. Match the
                            // unresolved source path so only this queue item receives its stream URL.
                            val updatedQueue = current.queue.map {
                                if (it.filePath == song.filePath) resolvedSong else it
                            }
                            current.copy(queue = updatedQueue, currentSong = resolvedSong)
                        }
                        playSongInternal(resolvedSong, sessionId, playbackRequestedAtNanos)
                    } else {
                        Log.e(TAG, "Failed to resolve online audio stream for YouTube ID: $videoId")
                        _uiState.update { it.copy(isLoadingOnlineStream = false, isPlaying = false) }
                        withContext(Dispatchers.Main) {
                            try {
                                android.widget.Toast.makeText(
                                    context,
                                    "Không thể phát bài hát này từ YouTube. Vui lòng chọn bài khác.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (t: Throwable) {}
                        }
                    }
                }
            }
            return
        }

        playSongInternal(song, sessionId, playbackRequestedAtNanos)
    }

    private fun playSongInternal(
        song: Song,
        sessionId: Long = currentPlaybackSessionId,
        playbackRequestedAtNanos: Long = System.nanoTime()
    ) {
        try {
            crossfadeJob?.cancel()
            crossfadeJob = null

            val isOnline = song.filePath.startsWith("http://") || song.filePath.startsWith("https://")
            val oldPlayer = mediaPlayer
            val state = _uiState.value
            val isCrossfade = state.isCrossfadeEnabled && oldPlayer != null && oldPlayer.isPlaying && !isOnline
            val crossfadeDurationMs = (state.crossfadeDurationSeconds * 1000).toLong().coerceIn(300L, 5000L)

            // Safely stop and release previous media players without canceling the active stream resolution coroutine
            if (!isCrossfade) {
                crossfadeJob?.cancel()
                crossfadeJob = null

                preparingPlayer?.let { p ->
                    try { p.setOnPreparedListener(null) } catch (t: Throwable) {}
                    try { p.setOnCompletionListener(null) } catch (t: Throwable) {}
                    try { p.setOnErrorListener(null) } catch (t: Throwable) {}
                    try { p.reset() } catch (t: Throwable) {}
                    try { p.release() } catch (t: Throwable) {}
                }
                preparingPlayer = null

                mediaPlayer?.let { p ->
                    try { p.setOnPreparedListener(null) } catch (t: Throwable) {}
                    try { p.setOnCompletionListener(null) } catch (t: Throwable) {}
                    try { p.setOnErrorListener(null) } catch (t: Throwable) {}
                    try {
                        if (p.isPlaying) p.stop()
                    } catch (t: Throwable) {}
                    try { p.reset() } catch (t: Throwable) {}
                    try { p.release() } catch (t: Throwable) {}
                }
                mediaPlayer = null
            } else {
                preparingPlayer?.let { p ->
                    try { p.setOnPreparedListener(null) } catch (t: Throwable) {}
                    try { p.setOnCompletionListener(null) } catch (t: Throwable) {}
                    try { p.setOnErrorListener(null) } catch (t: Throwable) {}
                    try { p.reset() } catch (t: Throwable) {}
                    try { p.release() } catch (t: Throwable) {}
                }
                preparingPlayer = null
            }

            val newPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                if (isOnline) {
                    try {
                        setDataSource(song.filePath)
                    } catch (e: Exception) {
                        val headers = mapOf("User-Agent" to com.example.data.YouTubeMusicService.AUDIO_USER_AGENT)
                        setDataSource(context, Uri.parse(song.filePath), headers)
                    }
                } else {
                    val file = File(song.filePath)
                    if (file.exists()) {
                        setDataSource(context, Uri.fromFile(file))
                    } else {
                        setDataSource(song.filePath)
                    }
                }

                // If crossfading, start new player silently and ramp up
                if (isCrossfade) {
                    setVolume(0f, 0f)
                } else {
                    setVolume(1f, 1f)
                }

                setOnPreparedListener { mp ->
                    // Guard against superseded / outdated players
                    if (currentPlaybackSessionId != sessionId || preparingPlayer != mp) {
                        try {
                            mp.reset()
                            mp.release()
                        } catch (t: Throwable) {}
                        return@setOnPreparedListener
                    }
                    preparingPlayer = null
                    mediaPlayer = mp

                    if (isOnline && song.format.contains("YouTube", ignoreCase = true)) {
                        Log.d(TAG, "YouTube stream prepared in ${(System.nanoTime() - playbackRequestedAtNanos) / 1_000_000} ms")
                    }

                    try {
                        mp.start()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error starting mediaPlayer: ${t.message}")
                        _uiState.update { it.copy(isLoadingOnlineStream = false, isPlaying = false) }
                        return@setOnPreparedListener
                    }

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
                            isLoadingOnlineStream = false,
                            totalDurationMs = mp.duration.toLong().coerceAtLeast(song.durationMs),
                            currentPositionMs = 0,
                            isHiResAudioActive = song.isHiRes
                        )
                    }
                    MusicPlaybackService.startOrUpdate(context, song, true, 0L)

                    if (isCrossfade && oldPlayer != null) {
                        // Smoothly crossfade: oldPlayer down, mp up
                        crossfadeJob = scope.launch {
                            val steps = 18
                            val interval = (crossfadeDurationMs / steps).coerceAtLeast(15L)
                            for (step in 1..steps) {
                                val progress = step.toFloat() / steps
                                val newVol = progress
                                val oldVol = (1.0f - progress).coerceAtLeast(0f)
                                try {
                                    mp.setVolume(newVol, newVol)
                                    oldPlayer.setVolume(oldVol, oldVol)
                                } catch (e: Exception) {
                                    // Player may have finished
                                }
                                delay(interval)
                            }
                            try {
                                mp.setVolume(1f, 1f)
                                oldPlayer.stop()
                                oldPlayer.release()
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                }

                setOnCompletionListener {
                    onSongCompleted()
                }

                setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    if (song.filePath.startsWith("http") && song.format.contains("YouTube", ignoreCase = true)) {
                        com.example.data.YouTubeMusicService.invalidateStreamUrl(song.filePath)
                    }
                    if (preparingPlayer == mp) {
                        preparingPlayer = null
                    }
                    if (mediaPlayer == mp) {
                        mediaPlayer = null
                    }
                    try {
                        mp.reset()
                        mp.release()
                    } catch (t: Throwable) {}

                    _uiState.update { it.copy(isLoadingOnlineStream = false, isPlaying = false) }
                    // CRUCIAL: Return true so Android does NOT invoke OnCompletionListener and cascade to songs below!
                    true
                }

                preparingPlayer = this
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing song: ${e.message}", e)
            if (song.filePath.startsWith("http") && song.format.contains("YouTube", ignoreCase = true)) {
                com.example.data.YouTubeMusicService.invalidateStreamUrl(song.filePath)
            }
            preparingPlayer = null
            _uiState.update { it.copy(isLoadingOnlineStream = false, isPlaying = false) }
        }
    }


    fun resumePlayback() {
        val player = mediaPlayer ?: run {
            val song = _uiState.value.currentSong ?: _uiState.value.queue.firstOrNull()
            if (song != null) playSong(song)
            return
        }
        if (!player.isPlaying) {
            requestAudioFocus()
            try {
                player.start()
                _uiState.update { it.copy(isPlaying = true) }
                _uiState.value.currentSong?.let {
                    MusicPlaybackService.startOrUpdate(context, it, true, currentPlaybackPosition())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Resume playback error: ${e.message}")
            }
        }
    }

    fun pausePlayback() {
        if (_uiState.value.isLoadingOnlineStream) {
            stopAllPlayback()
            _uiState.update { it.copy(isLoadingOnlineStream = false, isPlaying = false) }
            return
        }
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            try {
                player.pause()
                _uiState.update { it.copy(isPlaying = false) }
                _uiState.value.currentSong?.let {
                    MusicPlaybackService.startOrUpdate(context, it, false, currentPlaybackPosition())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pause playback error: ${e.message}")
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            val safePosition = positionMs.coerceAtLeast(0L)
            mediaPlayer?.seekTo(safePosition.toInt())
            _uiState.update { it.copy(currentPositionMs = safePosition) }
            _uiState.value.currentSong?.let { song ->
                MusicPlaybackService.startOrUpdate(context, song, _uiState.value.isPlaying, safePosition)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Seek error: ${e.message}")
        }
    }

    private fun currentPlaybackPosition(): Long = try {
        mediaPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L)
            ?: _uiState.value.currentPositionMs.coerceAtLeast(0L)
    } catch (_: Exception) {
        _uiState.value.currentPositionMs.coerceAtLeast(0L)
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

    fun setShuffleMode(enabled: Boolean) {
        _uiState.update { it.copy(isShuffle = enabled) }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isCrossfadeEnabled = enabled) }
    }

    fun setCrossfadeDuration(seconds: Float) {
        _uiState.update { it.copy(crossfadeDurationSeconds = seconds.coerceIn(0.5f, 5.0f)) }
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
        abandonAudioFocus()
        MusicPlaybackService.stop(context)
        if (activeInstance == this) activeInstance = null
        stopAllPlayback()
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        equalizerManager.release()
    }
}
