package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    ARTISTS("Nghệ sĩ")
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
    val librarySubTab: LibrarySubTab = LibrarySubTab.ALL_SONGS,
    val searchQuery: String = "",
    val songs: List<Song> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistSongs: List<Song> = emptyList(),
    val selectedFolder: MusicFolder? = null,
    val presets: List<EqualizerPreset> = emptyList(),
    val parsedLyrics: ParsedLyrics = ParsedLyrics(),
    val activeLyricIndex: Int = -1,
    val isScanning: Boolean = false,
    val scanResultMessage: String? = null,
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
    val youtubeQuery: String = "",
    val selectedYouTubeCategory: String = "🔥 Hot V-Pop",
    val youtubeSongs: List<Song> = emptyList(),
    val isSearchingYouTube: Boolean = false,
    val youtubeErrorMessage: String? = null
)


class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository(application)
    val equalizerManager = EqualizerManager(application)
    val playerController = MusicPlayerController(application, equalizerManager)

    private val _appUiState = MutableStateFlow(MusicAppUiState())
    val appUiState: StateFlow<MusicAppUiState> = _appUiState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.uiState
    val equalizerState: StateFlow<EqualizerState> = equalizerManager.state

    init {
        val savedKey = repository.getGeminiApiKey()
        val savedModel = repository.getGeminiModel()
        _appUiState.update { it.copy(geminiApiKey = savedKey, geminiModel = savedModel) }

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

        // Observe player position & song to parse and sync lyrics in real-time
        var lastObservedSongId: Long? = null
        var lastObservedLyrics: String? = null
        var lastObservedOffset: Long = 0
        val autoSearchedSongIds = mutableSetOf<Long>()

        viewModelScope.launch {
            playerState.collect { pState ->
                val song = pState.currentSong
                if (song != null) {
                    val songChanged = song.id != lastObservedSongId ||
                            song.lyrics != lastObservedLyrics ||
                            song.lrcOffsetMs != lastObservedOffset

                    if (songChanged) {
                        lastObservedSongId = song.id
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

                        // Tự động tìm kiếm lời bài hát trên internet nếu bài hát chưa có lời
                        if (song.lyrics.isNullOrBlank() && !autoSearchedSongIds.contains(song.id)) {
                            autoSearchedSongIds.add(song.id)
                            searchLyricsOnline(song, isAuto = true)
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
                }
            }
        }
    }

    fun setTab(tab: MainTab) {
        _appUiState.update { it.copy(currentTab = tab) }
        val needsHighPrecision = (tab == MainTab.NOW_PLAYING || tab == MainTab.LYRICS)
        playerController.setHighPrecisionTracking(needsHighPrecision)
        if (tab == MainTab.YOUTUBE && _appUiState.value.youtubeSongs.isEmpty() && !_appUiState.value.isSearchingYouTube) {
            selectYouTubeCategory(_appUiState.value.selectedYouTubeCategory)
        }
    }

    fun setLibrarySubTab(subTab: LibrarySubTab) {
        _appUiState.update { it.copy(librarySubTab = subTab, selectedFolder = null) }
    }

    fun setYouTubeQuery(query: String) {
        _appUiState.update { it.copy(youtubeQuery = query) }
    }

    fun searchYouTube(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
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
        _appUiState.update { it.copy(selectedYouTubeCategory = category, youtubeQuery = "") }
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
            playerController.playQueue(ytSongs, index)
        } else {
            playerController.playQueue(listOf(song), 0)
        }
    }

    fun setSearchQuery(query: String) {
        _appUiState.update { it.copy(searchQuery = query) }
    }


    fun scanDeviceAudio() {
        viewModelScope.launch {
            _appUiState.update { it.copy(isScanning = true, scanResultMessage = null) }
            val count = repository.scanDeviceAudioFiles()
            _appUiState.update {
                it.copy(
                    isScanning = false,
                    scanResultMessage = if (count > 0) "Đã quét và thêm $count bài hát mới vào thư viện" else "Thư viện đã được cập nhật đầy đủ"
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

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    // Playback Order controls
    fun playAllSequential(songs: List<Song>, startIndex: Int = 0) {
        playerController.playAllSequential(songs, startIndex)
    }

    fun playAllShuffled(songs: List<Song>) {
        playerController.playAllShuffled(songs)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        playerController.playQueue(songs, startIndex)
    }

    fun playSong(song: Song) {
        playerController.playSong(song)
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
        val currentBands = equalizerState.value.bands.joinToString(",") { it.levelMilliBels.toString() }
        val preset = EqualizerPreset(
            name = name,
            isCustom = true,
            bandLevelsCsv = currentBands,
            bassBoost = equalizerState.value.bassBoostStrength,
            virtualizer = equalizerState.value.virtualizerStrength,
            reverbPreset = 0
        )
        viewModelScope.launch {
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
                val updatedQueue = playerState.value.queue.map { if (it.id == songId) updated else it }
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
            val updatedQueue = playerState.value.queue.map { if (it.id == song.id) updated else it }
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
        _appUiState.update { it.copy(geminiApiKey = trimmed, scanResultMessage = "Đã lưu Gemini API Key thành công") }
    }

    fun setGeminiModel(model: String) {
        repository.setGeminiModel(model)
        _appUiState.update { it.copy(geminiModel = model, scanResultMessage = "Đã chọn mô hình: $model") }
    }

    fun searchLyricsOnline(song: Song, isAuto: Boolean = false) {
        viewModelScope.launch {
            if (!isAuto) {
                _appUiState.update { it.copy(isSearchingLyrics = true) }
            }
            val apiKey = _appUiState.value.geminiApiKey
            val preferredModel = _appUiState.value.geminiModel
            val result = com.example.lyrics.OnlineLyricsService.fetchLyrics(
                rawTitle = song.title,
                rawArtist = song.artist,
                durationMs = song.durationMs,
                apiKey = apiKey,
                preferredModel = preferredModel
            )
            if (!isAuto) {
                _appUiState.update { it.copy(isSearchingLyrics = false) }
            }

            if (result != null && result.bestLyrics != null) {
                val lyricsText = result.bestLyrics!!
                repository.updateSongLyrics(song.id, lyricsText, 0)
                // If this is the currently playing song, update currentSong in player
                val current = playerState.value.currentSong
                if (current != null && current.id == song.id) {
                    val updated = current.copy(lyrics = lyricsText, lrcOffsetMs = 0)
                    playerController.updateQueue(playerState.value.queue.map { if (it.id == song.id) updated else it })
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
                val formatType = if (!result.syncedLyrics.isNullOrBlank()) "Karaoke LRC đồng bộ" else "văn bản"
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
                if (current != null && current.id == song.id) {
                    val updated = current.copy(lyrics = lyricsText, lrcOffsetMs = 0)
                    playerController.updateQueue(playerState.value.queue.map { if (it.id == song.id) updated else it })
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
            LibrarySubTab.ALL_SONGS -> _appUiState.value.songs
            LibrarySubTab.FOLDERS -> _appUiState.value.songs
            LibrarySubTab.FAVORITES -> _appUiState.value.favoriteSongs
            LibrarySubTab.HI_RES -> _appUiState.value.songs.filter { it.isHiRes }
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

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
