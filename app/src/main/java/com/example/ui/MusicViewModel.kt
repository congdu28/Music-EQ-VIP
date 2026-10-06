package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.example.data.MusicRepository
import com.example.equalizer.EqualizerBand
import com.example.equalizer.EqualizerManager
import com.example.equalizer.EqualizerState
import com.example.lyrics.LrcParser
import com.example.lyrics.ParsedLyrics
import com.example.model.EqualizerPreset
import com.example.model.Playlist
import com.example.model.Song
import com.example.player.MusicPlayerController
import com.example.player.PlayerUiState
import com.example.ui.components.VisualizerStyle
import com.example.ui.theme.AppThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

enum class MainTab(val title: String) {
    LIBRARY("Thư viện"),
    YOUTUBE("YouTube"),
    NOW_PLAYING("Đang phát"),
    LYRICS("Lời bài hát"),
    EQUALIZER("Bộ chỉnh âm"),
    SETTINGS("Cài đặt")
}

enum class LibrarySubTab(val title: String) {
    ALL_SONGS("Tất cả"),
    FOLDERS("Thư mục"),
    HI_RES("Hi-Res FLAC/WAV"),
    FAVORITES("Yêu thích"),
    PLAYLISTS("Danh sách phát"),
    ARTISTS("Nghệ sĩ"),
    RECENT("Gần đây")
}

enum class LibrarySortOrder(val label: String) {
    TITLE_ASC("A–Z"),
    NEWEST("Mới nhất"),
    OLDEST("Cũ nhất")
}

data class MusicFolder(
    val name: String,
    val path: String,
    val songs: List<Song>
) {
    val songCount: Int get() = songs.size
    val hiResCount: Int get() = songs.count { it.isHiRes }
}

data class MusicAppUiState(
    val currentTab: MainTab = MainTab.LIBRARY,
    val pendingOnlineSong: Song? = null,
    val librarySubTab: LibrarySubTab = LibrarySubTab.ALL_SONGS,
    val librarySortOrder: LibrarySortOrder = LibrarySortOrder.TITLE_ASC,
    val searchQuery: String = "",
    val songs: List<Song> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
    val recentlyPlayedSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistSongs: List<Song> = emptyList(),
    val selectedFolder: MusicFolder? = null,
    val presets: List<EqualizerPreset> = emptyList(),
    val parsedLyrics: ParsedLyrics = ParsedLyrics(),
    val activeLyricIndex: Int = -1,
    val isScanning: Boolean = false,
    val scanResultMessage: String? = null,
    val hasCompletedLibraryScan: Boolean = false,
    val showInitialScanRecommendation: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val showAddToPlaylistDialog: Song? = null,
    val showEditMetadataDialog: Song? = null,
    val showEditLyricsDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showSavePresetDialog: Boolean = false,
    val showAudioSpecsDialog: Boolean = false,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.WAVE,
    val isSearchingLyrics: Boolean = false,
    val isAligningLyrics: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.7-flash",
    val isDarkTheme: Boolean = false,
    val appearanceMode: String = "LIGHT",
    val accentColor: Int = 0xFF3399FF.toInt(),
    val youtubeQuery: String = "",
    val youtubeSuggestions: List<String> = emptyList(),
    val selectedYouTubeCategory: String = "Tất cả",
    val youtubeSongs: List<Song> = emptyList(),
    val isSearchingYouTube: Boolean = false,
    val youtubeErrorMessage: String? = null
)


class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository(application)
    val equalizerManager = EqualizerManager(application)
    val playerController = MusicPlayerController(application, equalizerManager)

    private val _appUiState = MutableStateFlow(MusicAppUiState())
    private var pendingOnlinePlaybackJob: Job? = null
    val appUiState: StateFlow<MusicAppUiState> = _appUiState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.uiState
    val equalizerState: StateFlow<EqualizerState> = equalizerManager.state
    private var youtubeSuggestionJob: Job? = null

    init {
        val savedKey = repository.getGeminiApiKey()
        val savedModel = repository.getGeminiModel()
        val savedAppearanceMode = repository.getAppearanceMode()
        val systemDarkTheme = (application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val savedDarkTheme = when (savedAppearanceMode) {
            "DARK" -> true
            "SYSTEM" -> systemDarkTheme
            else -> false
        }
        val savedAccentColor = repository.getAccentColor()
        AppThemeColors.update(savedDarkTheme, Color(savedAccentColor))
        _appUiState.update {
            it.copy(
                geminiApiKey = savedKey,
                geminiModel = savedModel,
                isDarkTheme = savedDarkTheme,
                appearanceMode = savedAppearanceMode,
                accentColor = savedAccentColor,
                showInitialScanRecommendation = repository.shouldRecommendInitialLibraryScan(),
                hasCompletedLibraryScan = repository.hasCompletedLibraryScan(),
                recentlyPlayedSongs = repository.getRecentlyPlayedSongs()
            )
        }

        viewModelScope.launch {
            repository.initializeDefaultDataIfEmpty()
        }

        viewModelScope.launch {
            repository.allSongs.collect { songList ->
                _appUiState.update { it.copy(songs = songList) }
                // Pre-load queue and first song ready WITHOUT auto-playing
                if (playerController.uiState.value.currentSong == null && songList.isNotEmpty()) {
                    playerController.loadInitialQueue(songList, 0)
                } else if (playerController.uiState.value.queue.isEmpty() && songList.isNotEmpty()) {
                    playerController.updateQueue(songList)
                }
            }
        }

        viewModelScope.launch {
            repository.favoriteSongs.collect { favs ->
                _appUiState.update { it.copy(favoriteSongs = favs) }
            }
        }

        viewModelScope.launch {
            repository.allPlaylists.collect { pLists ->
                _appUiState.update { it.copy(playlists = pLists) }
            }
        }

        viewModelScope.launch {
            repository.allPresets.collect { presetList ->
                _appUiState.update { it.copy(presets = presetList) }
            }
        }

        // Keep completion notices visible long enough to read, then clear them automatically.
        viewModelScope.launch {
            _appUiState
                .map { it.scanResultMessage }
                .distinctUntilChanged()
                .collectLatest { message ->
                    if (message != null) {
                        delay(6_000)
                        _appUiState.update { state ->
                            if (state.scanResultMessage == message) state.copy(scanResultMessage = null) else state
                        }
                    }
                }
        }

        // Observe player position & song to parse and sync lyrics in real-time
        var lastObservedSongPath: String? = null
        var lastObservedLyrics: String? = null
        var lastObservedOffset: Long = 0
        // YouTube entries replace yt://videoId with a resolved stream URL once
        // playback starts. Use the stable song ID so that transition cannot
        // trigger a second automatic lookup or clear a just-fetched lyric.
        val autoSearchedSongKeys = mutableSetOf<String>()
        var lastRecordedTrackId: Long? = null

        viewModelScope.launch {
            playerState.collect { pState ->
                val song = pState.currentSong
                if (song != null) {
                    if (pState.isPlaying && !pState.isLoadingOnlineStream && lastRecordedTrackId != song.id) {
                        lastRecordedTrackId = song.id
                        val recentSongs = repository.recordRecentlyPlayed(song.asReusableHistoryEntry())
                        _appUiState.update { it.copy(recentlyPlayedSongs = recentSongs) }
                    }
                    val songChanged = song.filePath != lastObservedSongPath ||
                            song.lyrics != lastObservedLyrics ||
                            song.lrcOffsetMs != lastObservedOffset

                    if (songChanged) {
                        lastObservedSongPath = song.filePath
                        lastObservedLyrics = song.lyrics
                        lastObservedOffset = song.lrcOffsetMs

                        val parsed = LrcParser.parse(song.lyrics, song.lrcOffsetMs)
                        val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, pState.currentPositionMs)
                        _appUiState.update {
                            it.copy(
                                parsedLyrics = parsed,
                                activeLyricIndex = activeIdx
                            )
                        }

                    } else {
                        // Song unchanged, only position progressed
                        val parsed = _appUiState.value.parsedLyrics
                        val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, pState.currentPositionMs)
                        if (activeIdx != _appUiState.value.activeLyricIndex) {
                            _appUiState.update {
                                it.copy(activeLyricIndex = activeIdx)
                            }
                        }
                    }

                    // Start automatic lookup after an online track has finished
                    // resolving and preparing. If lookup starts while the
                    // YouTube URL is still being replaced, player preparation
                    // can publish an older Song value over the lyrics result.
                    val isDemoPreview = song.album == "Bài nghe thử" && song.artist == "Music EQ"
                    if (song.lyrics.isNullOrBlank() && !pState.isLoadingOnlineStream && !isDemoPreview &&
                        autoSearchedSongKeys.add(lyricsTrackKey(song))) {
                        searchLyricsOnline(song, isAuto = true)
                    }
                }
            }
        }
    }

    fun setTab(tab: MainTab) {
        _appUiState.update { it.copy(currentTab = tab) }
        val needsHighPrecision = (tab == MainTab.NOW_PLAYING || tab == MainTab.LYRICS)
        playerController.setHighPrecisionTracking(needsHighPrecision)
        if (tab == MainTab.YOUTUBE) {
            // Warm the playback session while results load, so tapping a result
            // usually skips the separate visitor-token request.
            viewModelScope.launch(Dispatchers.IO) {
                com.example.data.YouTubeMusicService.getVisitorData()
            }
            if (_appUiState.value.youtubeSongs.isEmpty() && !_appUiState.value.isSearchingYouTube) {
                selectYouTubeCategory(_appUiState.value.selectedYouTubeCategory)
            }
        }
    }

    fun setLibrarySubTab(subTab: LibrarySubTab) {
        _appUiState.update { it.copy(librarySubTab = subTab, selectedFolder = null) }
    }

    fun setLibrarySortOrder(sortOrder: LibrarySortOrder) {
        _appUiState.update { it.copy(librarySortOrder = sortOrder) }
    }

    fun setYouTubeQuery(query: String) {
        _appUiState.update { it.copy(youtubeQuery = query, youtubeSuggestions = emptyList()) }
        youtubeSuggestionJob?.cancel()
        if (query.isBlank()) return

        youtubeSuggestionJob = viewModelScope.launch {
            delay(80)
            val suggestions = com.example.data.YouTubeMusicService.searchSuggestions(query)
            if (_appUiState.value.youtubeQuery == query) {
                _appUiState.update { it.copy(youtubeSuggestions = suggestions) }
            }
        }
    }

    fun searchYouTube(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        youtubeSuggestionJob?.cancel()
        _appUiState.update { it.copy(youtubeSuggestions = emptyList()) }
        viewModelScope.launch {
            try {
                _appUiState.update { it.copy(isSearchingYouTube = true, youtubeErrorMessage = null, youtubeQuery = trimmed) }
                val results = try {
                    com.example.data.YouTubeMusicService.searchSongs(trimmed)
                } catch (t: Throwable) {
                    android.util.Log.e("MusicViewModel", "Error searching YouTube: ${t.message}", t)
                    emptyList()
                }
                _appUiState.update {
                    it.copy(
                        isSearchingYouTube = false,
                        youtubeSongs = results,
                        youtubeErrorMessage = if (results.isEmpty()) "Không tìm thấy bài hát nào trên YouTube cho '$trimmed'" else null
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.e("MusicViewModel", "Error in searchYouTube: ${t.message}", t)
                _appUiState.update {
                    it.copy(
                        isSearchingYouTube = false,
                        youtubeErrorMessage = "Lỗi tìm kiếm YouTube: ${t.message}"
                    )
                }
            }
        }
    }

    fun selectYouTubeCategory(category: String) {
        youtubeSuggestionJob?.cancel()
        _appUiState.update {
            it.copy(
                selectedYouTubeCategory = category,
                youtubeQuery = "",
                youtubeSuggestions = emptyList(),
                youtubeSongs = it.youtubeSongs,
                youtubeErrorMessage = null,
                isSearchingYouTube = true
            )
        }
        viewModelScope.launch {
            try {
                _appUiState.update { it.copy(isSearchingYouTube = true, youtubeErrorMessage = null) }
                val query = com.example.data.YouTubeMusicService.getCategoryQuery(category)
                val results = try {
                    com.example.data.YouTubeMusicService.searchSongs(query)
                } catch (t: Throwable) {
                    android.util.Log.e("MusicViewModel", "Error fetching YouTube category songs: ${t.message}", t)
                    emptyList()
                }
                _appUiState.update {
                    it.copy(
                        isSearchingYouTube = false,
                        youtubeSongs = results,
                        youtubeErrorMessage = if (results.isEmpty()) "Không thể tải danh mục '$category' từ YouTube. Vui lòng thử lại." else null
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.e("MusicViewModel", "Error in selectYouTubeCategory: ${t.message}", t)
                _appUiState.update {
                    it.copy(
                        isSearchingYouTube = false,
                        youtubeErrorMessage = "Lỗi tải YouTube: ${t.message}"
                    )
                }
            }
        }
    }

    fun playYouTubeSong(song: Song) {
        val ytSongs = _appUiState.value.youtubeSongs
        val index = ytSongs.indexOfFirst { it.filePath == song.filePath }
        if (index >= 0) {
            playQueue(ytSongs, index)
        } else {
            playQueue(listOf(song), 0)
        }
    }

    fun setSearchQuery(query: String) {
        _appUiState.update { it.copy(searchQuery = query) }
    }


    fun scanDeviceAudio() {
        viewModelScope.launch {
            _appUiState.update { it.copy(isScanning = true, scanResultMessage = null) }
            val count = repository.scanDeviceAudioFiles()
            repository.markLibraryScanCompleted()
            _appUiState.update {
                it.copy(
                    isScanning = false,
                    hasCompletedLibraryScan = true,
                    scanResultMessage = if (count > 0) "Đã quét và thêm $count bài hát mới vào thư viện" else "Thư viện đã được cập nhật đầy đủ"
                )
            }
        }
    }

    fun scanAudioFolder(folderUri: Uri) {
        viewModelScope.launch {
            _appUiState.update { it.copy(isScanning = true, scanResultMessage = null) }
            val count = repository.scanAudioFolder(folderUri)
            repository.markLibraryScanCompleted()
            _appUiState.update {
                it.copy(
                    isScanning = false,
                    hasCompletedLibraryScan = true,
                    scanResultMessage = if (count > 0) "Đã quét thư mục và thêm $count bài hát mới vào thư viện" else "Không tìm thấy bài hát mới trong thư mục đã chọn"
                )
            }
        }
    }

    fun scanDeviceAudioIfEmpty() {
        viewModelScope.launch {
            if (_appUiState.value.songs.isEmpty()) {
                scanDeviceAudio()
            }
        }
    }

    fun dismissScanMessage() {
        _appUiState.update { it.copy(scanResultMessage = null) }
    }

    fun dismissInitialScanRecommendation() {
        repository.markInitialLibraryScanRecommendationSeen()
        _appUiState.update { it.copy(showInitialScanRecommendation = false) }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val isFavorite = repository.toggleFavorite(song)
            playerController.updateFavoriteState(song.id, isFavorite)
            val recentSongs = _appUiState.value.recentlyPlayedSongs.map { recent ->
                if (recent.id == song.id) recent.copy(isFavorite = isFavorite) else recent
            }
            repository.replaceRecentlyPlayedSongs(recentSongs)
            _appUiState.update { it.copy(recentlyPlayedSongs = recentSongs) }
        }
    }

    // Playback Order controls
    fun playAllSequential(songs: List<Song>, startIndex: Int = 0) {
        clearPendingOnlineSelection()
        setTab(MainTab.NOW_PLAYING)
        playerController.playAllSequential(songs, startIndex)
    }

    fun playAllShuffled(songs: List<Song>) {
        clearPendingOnlineSelection()
        setTab(MainTab.NOW_PLAYING)
        playerController.playAllShuffled(songs)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        clearPendingOnlineSelection()
        setTab(MainTab.NOW_PLAYING)
        playerController.playQueue(songs, startIndex)
    }

    /** Keep the playlist detail visible so playback can use the persistent mini-player. */
    fun playPlaylistQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        clearPendingOnlineSelection()
        _appUiState.update { it.copy(currentTab = MainTab.LIBRARY) }
        playerController.playQueue(songs, startIndex)
    }

    fun playFavoriteQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val selectedIndex = startIndex.coerceIn(0, songs.lastIndex)
        val selectedSong = songs[selectedIndex]
        val isYouTubeFavorite = selectedSong.filePath.startsWith("yt://") ||
            selectedSong.format.contains("YouTube", ignoreCase = true)

        clearPendingOnlineSelection()
        if (!isYouTubeFavorite) {
            playQueue(songs, selectedIndex)
            return
        }

        // Publish a screen-level loading selection before entering player cleanup/resolution.
        // That work can block on some devices, so the player screen must not depend on the
        // controller's currentSong changing first.
        _appUiState.update {
            it.copy(currentTab = MainTab.NOW_PLAYING, pendingOnlineSong = selectedSong)
        }
        pendingOnlinePlaybackJob = viewModelScope.launch {
            try {
                // Give Compose a frame to show the pending song before MediaPlayer teardown.
                delay(64)
                playerController.playQueue(songs, selectedIndex)
                // Some saved YouTube favorites already contain a resolved HTTP stream URL.
                // That path prepares asynchronously without setting isLoadingOnlineStream or
                // publishing currentSong immediately, so keep the pending screen until either
                // the selected favorite is ready or playback fails.
                withTimeoutOrNull(45_000L) {
                    playerState.first { state ->
                        val current = state.currentSong
                        val selectedIsReady = current?.id == selectedSong.id &&
                            current.title == selectedSong.title &&
                            current.artist == selectedSong.artist &&
                            !state.isLoadingOnlineStream
                        val playbackFailed = current?.id != selectedSong.id &&
                            !state.isPlaying && !state.isLoadingOnlineStream
                        selectedIsReady || playbackFailed
                    }
                }
            } finally {
                _appUiState.update { state ->
                    if (state.pendingOnlineSong?.filePath == selectedSong.filePath) {
                        state.copy(pendingOnlineSong = null)
                    } else state
                }
            }
        }
    }

    fun playSong(song: Song) {
        clearPendingOnlineSelection()
        setTab(MainTab.NOW_PLAYING)
        playerController.playSong(song)
    }

    private fun clearPendingOnlineSelection() {
        pendingOnlinePlaybackJob?.cancel()
        pendingOnlinePlaybackJob = null
        if (_appUiState.value.pendingOnlineSong != null) {
            _appUiState.update { it.copy(pendingOnlineSong = null) }
        }
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun pausePlayback() = playerController.pausePlayback()
    fun resumePlayback() = playerController.resumePlayback()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun playNext() = playerController.playNext()
    fun playPrevious() = playerController.playPrevious()
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeatMode() = playerController.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)
    fun startSleepTimer(minutes: Int) = playerController.startSleepTimer(minutes)
    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    // Smooth Crossfade controls
    fun setCrossfadeEnabled(enabled: Boolean) = playerController.setCrossfadeEnabled(enabled)
    fun setCrossfadeDuration(seconds: Float) = playerController.setCrossfadeDuration(seconds)

    // Visualizer Style
    fun toggleVisualizerStyle() {
        val nextStyle = if (_appUiState.value.visualizerStyle == VisualizerStyle.WAVE) {
            VisualizerStyle.SPECTRUM
        } else {
            VisualizerStyle.WAVE
        }
        _appUiState.update { it.copy(visualizerStyle = nextStyle) }
    }

    // Folder Browsing
    fun getMusicFolders(): List<MusicFolder> {
        val allSongs = _appUiState.value.songs
        if (allSongs.isEmpty()) return emptyList()

        val folderMap = mutableMapOf<String, MutableList<Song>>()
        for (song in allSongs) {
            val path = song.filePath
            val parentPath = try {
                val f = File(path)
                f.parentFile?.absolutePath ?: "Bộ nhớ máy"
            } catch (e: Exception) {
                "Bộ nhớ máy"
            }
            folderMap.getOrPut(parentPath) { mutableListOf() }.add(song)
        }

        return folderMap.map { (path, songs) ->
            val folderName = try {
                val f = File(path)
                when {
                    f.name.isBlank() || f.name == "/" -> "Bộ nhớ trong"
                    path.contains("files") || path.contains("synthetic") -> "Bộ sưu tập Hi-Res Studio"
                    else -> f.name
                }
            } catch (e: Exception) {
                "Thư mục âm nhạc"
            }
            MusicFolder(name = folderName, path = path, songs = songs)
        }.sortedBy { it.name }
    }

    fun openFolder(folder: MusicFolder) {
        _appUiState.update { it.copy(selectedFolder = folder) }
    }

    fun closeFolder() {
        _appUiState.update { it.copy(selectedFolder = null) }
    }

    // Song Metadata Tag Editor
    fun setShowEditMetadata(song: Song?) {
        _appUiState.update { it.copy(showEditMetadataDialog = song) }
    }

    fun saveSongMetadata(songId: Long, title: String, artist: String, album: String, format: String) {
        viewModelScope.launch {
            val updated = repository.updateSongMetadata(songId, title, artist, album, format)
            if (updated != null) {
                // If currently playing, update in playerController queue/currentSong
                val current = playerState.value.currentSong
                if (current != null && current.id == songId) {
                    playerController.updateQueue(playerState.value.queue.map { if (it.id == songId) updated else it })
                }
                _appUiState.update {
                    it.copy(
                        showEditMetadataDialog = null,
                        scanResultMessage = "Đã lưu thông tin: $title"
                    )
                }
            } else {
                _appUiState.update { it.copy(showEditMetadataDialog = null) }
            }
        }
    }

    // Equalizer controls
    fun toggleEqualizerEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)
    fun updateBandLevel(bandIndex: Short, levelMilliBels: Short) = equalizerManager.updateBandLevel(bandIndex, levelMilliBels)
    fun setBandLevel(bandIndex: Int, levelDb: Float) = equalizerManager.updateBandLevel(bandIndex.toShort(), (levelDb * 100).toInt().toShort())
    fun setBassBoost(strength: Int) = equalizerManager.setBassBoost(strength)
    fun setVirtualizer(strength: Int) = equalizerManager.setVirtualizer(strength)
    fun setLoudnessBoost(gainMilliBels: Int) = equalizerManager.setLoudnessBoost(gainMilliBels)
    fun setReverbPreset(preset: Short) = equalizerManager.setReverbPreset(preset)
    fun setPreAmpLevel(levelDb: Float) = equalizerManager.setPreAmpLevel((levelDb * 100).toInt().toShort())
    fun resetEqualizer() = equalizerManager.resetToFlat()
    fun resetEqualizerToFlat() = equalizerManager.resetToFlat()

    fun applyPreset(preset: EqualizerPreset) {
        val levels = preset.bandLevelsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        equalizerManager.applyPreset(preset.name, levels, preset.bassBoost, preset.virtualizer)
        equalizerManager.setReverbPreset(preset.reverbPreset.toShort())
    }

    fun applyPreset(presetName: String) {
        val preset = _appUiState.value.presets.firstOrNull { it.name == presetName }
        if (preset != null) {
            applyPreset(preset)
        } else {
            val defaultGains = when (presetName) {
                "Bass Boost" -> listOf(600, 400, 100, 0, 0)
                "Pop" -> listOf(100, 300, 500, 200, 100)
                "Rock" -> listOf(500, 300, -100, 400, 600)
                "EDM" -> listOf(700, 300, 0, 400, 500)
                "Acoustic" -> listOf(300, 200, 200, 300, 200)
                "Jazz" -> listOf(300, 100, -100, 200, 400)
                "Classical" -> listOf(400, 200, -100, 200, 400)
                else -> listOf(0, 0, 0, 0, 0)
            }
            val defaultBass = if (presetName == "Bass Boost" || presetName == "EDM") 600 else 100
            val defaultVirtual = if (presetName == "EDM" || presetName == "Rock") 400 else 100
            equalizerManager.applyPreset(presetName, defaultGains, defaultBass, defaultVirtual)
        }
    }

    fun saveCurrentPreset(name: String) {
        viewModelScope.launch {
            val state = equalizerState.value
            val existing = _appUiState.value.presets.firstOrNull {
                it.isCustom && it.name.equals(name, ignoreCase = true)
            }
            val preset = EqualizerPreset(
                id = existing?.id ?: 0,
                name = name,
                isCustom = true,
                bandLevelsCsv = state.bands.joinToString(",") { it.levelMilliBels.toString() },
                bassBoost = state.bassBoostStrength,
                virtualizer = state.virtualizerStrength,
                reverbPreset = state.reverbPreset.toInt()
            )
            repository.saveEqualizerPreset(preset)
            _appUiState.update { it.copy(showSavePresetDialog = false) }
        }
    }

    // Lyrics controls
    fun updateLyrics(songId: Long, lyrics: String, offsetMs: Long) {
        viewModelScope.launch {
            repository.updateSongLyrics(songId, lyrics, offsetMs)
            val current = playerState.value.currentSong
            if (current != null && current.id == songId) {
                val updated = current.copy(lyrics = lyrics, lrcOffsetMs = offsetMs)
                val updatedQueue = playerState.value.queue.map {
                    if (it.filePath == current.filePath) updated else it
                }
                playerController.updateQueue(updatedQueue)
                val parsed = LrcParser.parse(lyrics, offsetMs)
                val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, playerState.value.currentPositionMs)
                _appUiState.update {
                    it.copy(
                        parsedLyrics = parsed,
                        activeLyricIndex = activeIdx,
                        showEditLyricsDialog = false,
                        scanResultMessage = "Đã cập nhật lời bài hát thành công"
                    )
                }
            } else {
                _appUiState.update {
                    it.copy(showEditLyricsDialog = false, scanResultMessage = "Đã lưu lời bài hát")
                }
            }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        val song = playerState.value.currentSong ?: return
        val newOffset = song.lrcOffsetMs + deltaMs
        viewModelScope.launch {
            repository.updateSongLyrics(song.id, song.lyrics ?: "", newOffset)
            val updated = song.copy(lrcOffsetMs = newOffset)
            val updatedQueue = playerState.value.queue.map {
                if (it.filePath == song.filePath) updated else it
            }
            playerController.updateQueue(updatedQueue)
            val parsed = LrcParser.parse(song.lyrics ?: "", newOffset)
            val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, playerState.value.currentPositionMs)
            _appUiState.update {
                it.copy(
                    parsedLyrics = parsed,
                    activeLyricIndex = activeIdx
                )
            }
        }
    }

    fun setGeminiApiKey(key: String) {
        val trimmed = key.trim()
        repository.setGeminiApiKey(trimmed)
        _appUiState.update {
            it.copy(
                geminiApiKey = repository.getGeminiApiKey(),
                scanResultMessage = if (trimmed.isBlank()) "Đã chuyển sang API Key tích hợp" else "Đã lưu Gemini API Key riêng"
            )
        }
    }

    fun setGeminiModel(model: String) {
        repository.setGeminiModel(model)
        _appUiState.update { it.copy(geminiModel = model, scanResultMessage = "Đã chọn mô hình: $model") }
    }

    fun searchLyricsOnline(song: Song, isAuto: Boolean = false) {
        viewModelScope.launch {
            _appUiState.update { it.copy(isSearchingLyrics = true) }
            val apiKey = _appUiState.value.geminiApiKey
            val preferredModel = _appUiState.value.geminiModel
            val result = try {
                com.example.lyrics.OnlineLyricsService.fetchLyrics(
                    rawTitle = song.title,
                    rawArtist = song.artist,
                    durationMs = song.durationMs,
                    apiKey = apiKey,
                    preferredModel = preferredModel
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.w("MusicViewModel", "Lyrics lookup failed for '${song.title}': ${t.message}", t)
                null
            }
            _appUiState.update { it.copy(isSearchingLyrics = false) }

            if (result != null && result.bestLyrics != null) {
                val lyricsText = result.syncedLyrics?.takeIf { it.isNotBlank() }
                    ?: result.plainLyrics?.let {
                        LrcParser.convertPlainTextToSyncedLrc(it, song.durationMs)
                    }
                    ?: result.bestLyrics!!
                repository.updateSongLyrics(song.id, lyricsText, 0)
                // If this is the currently playing song, update currentSong in player
                val current = playerState.value.currentSong
                if (current != null && isSameLyricsTrack(current, song)) {
                    playerController.updateQueue(playerState.value.queue.map {
                        if (isSameLyricsTrack(it, song)) it.copy(lyrics = lyricsText, lrcOffsetMs = 0) else it
                    })
                    // Also parse into uiState immediately
                    val parsed = LrcParser.parse(lyricsText, 0)
                    val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, playerState.value.currentPositionMs)
                    _appUiState.update {
                        it.copy(
                            parsedLyrics = parsed,
                            activeLyricIndex = activeIdx
                        )
                    }
                }
                val formatType = if (!result.syncedLyrics.isNullOrBlank()) {
                    "Karaoke LRC đồng bộ"
                } else {
                    "lời căn thời gian ước tính"
                }
                val prefix = if (isAuto) "Tự động tải lời" else "Đã tìm thấy lời"
                _appUiState.update {
                    it.copy(scanResultMessage = "$prefix $formatType từ internet cho: ${song.title}")
                }
            } else if (!isAuto) {
                _appUiState.update {
                    it.copy(scanResultMessage = "Không tìm thấy lời trên mạng cho: ${song.title}")
                }
            }
        }
    }

    fun searchLyricsWithGemini(song: Song) {
        viewModelScope.launch {
            _appUiState.update { it.copy(isSearchingLyrics = true) }
            val apiKey = _appUiState.value.geminiApiKey
            val preferredModel = _appUiState.value.geminiModel
            val result = com.example.lyrics.OnlineLyricsService.fetchLyricsWithGemini(
                cleanTitle = com.example.lyrics.OnlineLyricsService.cleanSearchTerm(song.title),
                cleanArtist = com.example.lyrics.OnlineLyricsService.cleanSearchTerm(song.artist),
                durationMs = song.durationMs,
                apiKey = apiKey,
                preferredModel = preferredModel
            )
            _appUiState.update { it.copy(isSearchingLyrics = false) }

            if (result != null && !result.syncedLyrics.isNullOrBlank()) {
                val lyricsText = result.syncedLyrics
                repository.updateSongLyrics(song.id, lyricsText, 0)
                val current = playerState.value.currentSong
                if (current != null && isSameLyricsTrack(current, song)) {
                    playerController.updateQueue(playerState.value.queue.map {
                        if (isSameLyricsTrack(it, song)) it.copy(lyrics = lyricsText, lrcOffsetMs = 0) else it
                    })
                    val parsed = LrcParser.parse(lyricsText, 0)
                    val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, playerState.value.currentPositionMs)
                    _appUiState.update {
                        it.copy(
                            parsedLyrics = parsed,
                            activeLyricIndex = activeIdx
                        )
                    }
                }
                _appUiState.update {
                    it.copy(scanResultMessage = "Gemini AI (${preferredModel}) đã tạo lời Karaoke LRC thành công cho: ${song.title}")
                }
            } else {
                _appUiState.update {
                    it.copy(scanResultMessage = "Gemini AI chưa thể tạo lời cho bài này, vui lòng thử lại hoặc chọn mô hình khác")
                }
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        setAppearanceMode(if (enabled) "DARK" else "LIGHT")
    }

    fun setAppearanceMode(mode: String) {
        val normalized = mode.takeIf { it in setOf("LIGHT", "DARK", "SYSTEM") } ?: "LIGHT"
        val systemDarkTheme = (getApplication<Application>().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = when (normalized) {
            "DARK" -> true
            "SYSTEM" -> systemDarkTheme
            else -> false
        }
        repository.setAppearanceMode(normalized)
        AppThemeColors.update(dark = dark)
        _appUiState.update { it.copy(appearanceMode = normalized, isDarkTheme = dark) }
    }

    fun applyResolvedAppearance(dark: Boolean) {
        AppThemeColors.update(dark = dark)
        _appUiState.update { state -> if (state.isDarkTheme == dark) state else state.copy(isDarkTheme = dark) }
    }

    fun setAccentColor(color: Int) {
        repository.setAccentColor(color)
        AppThemeColors.update(accentColor = Color(color))
        _appUiState.update { it.copy(accentColor = color) }
    }

    private fun lyricsTrackKey(song: Song): String = "${song.id}|${song.title}|${song.artist}"

    private fun isSameLyricsTrack(first: Song, second: Song): Boolean =
        first.filePath == second.filePath ||
            (first.id == second.id && first.title == second.title && first.artist == second.artist)

    fun alignLyricsWithGemini(rawLyrics: String, onComplete: ((String?) -> Unit)? = null) {
        val currentSong = playerState.value.currentSong ?: return
        viewModelScope.launch {
            _appUiState.update { it.copy(isAligningLyrics = true) }
            val apiKey = _appUiState.value.geminiApiKey
            val preferredModel = _appUiState.value.geminiModel
            val alignedLrc = com.example.lyrics.OnlineLyricsService.alignLyricsWithGemini(
                plainLyrics = rawLyrics,
                cleanTitle = com.example.lyrics.OnlineLyricsService.cleanSearchTerm(currentSong.title),
                cleanArtist = com.example.lyrics.OnlineLyricsService.cleanSearchTerm(currentSong.artist),
                durationMs = currentSong.durationMs,
                apiKey = apiKey,
                preferredModel = preferredModel
            )
            _appUiState.update { it.copy(isAligningLyrics = false) }
            if (!alignedLrc.isNullOrBlank()) {
                updateLyrics(currentSong.id, alignedLrc, 0)
                _appUiState.update {
                    it.copy(scanResultMessage = "Gemini AI (${preferredModel}) đã căn chỉnh mốc thời gian bài hát chuẩn xác!")
                }
                onComplete?.invoke(alignedLrc)
            } else {
                _appUiState.update {
                    it.copy(scanResultMessage = "Không thể căn chỉnh bằng AI lúc này, vui lòng thử mô hình khác hoặc kiểm tra mạng")
                }
                onComplete?.invoke(null)
            }
        }
    }

    // Playlists
    fun openPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.getSongsForPlaylist(playlist.id).collect { songs ->
                _appUiState.update {
                    it.copy(
                        selectedPlaylist = playlist,
                        selectedPlaylistSongs = songs
                    )
                }
            }
        }
    }

    fun closePlaylist() {
        _appUiState.update { it.copy(selectedPlaylist = null, selectedPlaylistSongs = emptyList()) }
    }

    fun createPlaylist(name: String, desc: String) {
        viewModelScope.launch {
            repository.createPlaylist(name, desc)
            _appUiState.update { it.copy(showCreatePlaylistDialog = false) }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            _appUiState.update { it.copy(showAddToPlaylistDialog = null) }
        }
    }

    fun setShowCreatePlaylist(show: Boolean) = _appUiState.update { it.copy(showCreatePlaylistDialog = show) }
    fun setShowAddToPlaylist(song: Song?) = _appUiState.update { it.copy(showAddToPlaylistDialog = song) }
    fun setShowEditLyrics(show: Boolean) = _appUiState.update { it.copy(showEditLyricsDialog = show) }
    fun setShowSleepTimer(show: Boolean) = _appUiState.update { it.copy(showSleepTimerDialog = show) }
    fun setShowSavePreset(show: Boolean) = _appUiState.update { it.copy(showSavePresetDialog = show) }
    fun setShowAudioSpecs(show: Boolean) = _appUiState.update { it.copy(showAudioSpecsDialog = show) }

    fun getFilteredSongs(): List<Song> {
        val query = _appUiState.value.searchQuery.trim().lowercase()
        val all = when (_appUiState.value.librarySubTab) {
            LibrarySubTab.ALL_SONGS -> _appUiState.value.songs.filterNot { it.isFavorite }
            LibrarySubTab.FOLDERS -> _appUiState.value.songs
            LibrarySubTab.FAVORITES -> _appUiState.value.favoriteSongs
            LibrarySubTab.HI_RES -> _appUiState.value.songs.filter { it.isHiRes }
            LibrarySubTab.RECENT -> _appUiState.value.recentlyPlayedSongs
            LibrarySubTab.ARTISTS, LibrarySubTab.PLAYLISTS -> _appUiState.value.songs
        }
        if (query.isEmpty()) return all
        return all.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query) ||
            it.album.lowercase().contains(query) ||
            it.format.lowercase().contains(query)
        }
    }

    private fun Song.asReusableHistoryEntry(): Song {
        if (!format.contains("YouTube", ignoreCase = true) || filePath.startsWith("yt://")) return this
        val videoId = albumArtUri
            ?.substringAfter("/vi/", "")
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
            ?: return this
        return copy(filePath = "yt://$videoId")
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
