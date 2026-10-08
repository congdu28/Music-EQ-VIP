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
    YOUTUBE_DOWNLOADS("Offline Youtube"),
    RECENT("Gần đây")
}

enum class LibrarySortOrder(val label: String) {
    TITLE_ASC("A–Z"),
    NEWEST("Mới nhất"),
    OLDEST("Cũ nhất")
}

enum class YouTubeDownloadQuality(val bitrateKbps: Int, val label: String) {
    STANDARD_128(128, "128 kbps · tiết kiệm dung lượng"),
    HIGH_320(320, "320 kbps · cao nhất có sẵn")
}

enum class PlayerAmbientMode(val label: String) {
    OFF("Tắt"),
    ALBUM("Ảnh bìa"),
    RGB("RGB"),
    AURORA("Aurora"),
    ACCENT("Màu nhấn")
}

enum class PlayerAmbientStyle(val label: String) {
    GLOW("Tỏa sáng"),
    WAVE("Sóng"),
    ROTATE("Xoay vòng"),
    AURORA("Cực quang"),
    PULSE("Nhịp thở"),
    DIAGONAL("Dải sáng"),
    RINGS("Vòng sáng"),
    PARTICLES("Hạt sáng"),
    NEBULA("Tinh vân"),
    FIREWORKS("Pháo hoa"),
    ORBS("Quả cầu sáng"),
    PRISM("Lăng kính")
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
    val pendingPlaylistSongForCreation: Song? = null,
    val showEditMetadataDialog: Song? = null,
    val showDeleteSongDialog: Song? = null,
    val showRemoveFromPlaylistDialog: Song? = null,
    val showEditLyricsDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showSavePresetDialog: Boolean = false,
    val showAudioSpecsDialog: Boolean = false,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.WAVE,
    val isSearchingLyrics: Boolean = false,
    val geminiLyricsSuggestionSongId: Long? = null,
    val isAligningLyrics: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.8-flash",
    val isDarkTheme: Boolean = false,
    val appearanceMode: String = "SYSTEM",
    val accentColor: Int = 0xFF3399FF.toInt(),
    val playerAmbientMode: PlayerAmbientMode = PlayerAmbientMode.OFF,
    val playerAmbientStyle: PlayerAmbientStyle = PlayerAmbientStyle.GLOW,
    val playerAmbientSpeed: Float = 1f,
    val youtubeQuery: String = "",
    val youtubeSuggestions: List<String> = emptyList(),
    val selectedYouTubeCategory: String = "🔥 Hot V-Pop",
    val youtubeSongs: List<Song> = emptyList(),
    val isSearchingYouTube: Boolean = false,
    val isLoadingMoreYouTube: Boolean = false,
    val youtubeContinuation: String? = null,
    val youtubePaginationError: String? = null,
    val youtubeErrorMessage: String? = null,
    val youtubeDownloadProgress: Map<String, Int> = emptyMap(),
    val youtubeDownloadBytes: Map<String, Long> = emptyMap(),
    val downloadedYouTubeVideoIds: Set<String> = emptySet()
)


class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository(application)
    private val youtubeDownloadNotifier = com.example.data.YouTubeDownloadNotifier(application)
    val equalizerManager = EqualizerManager(application)
    val playerController = MusicPlayerController(application, equalizerManager)

    private val _appUiState = MutableStateFlow(MusicAppUiState())
    private var pendingOnlinePlaybackJob: Job? = null
    private val tabBackStack = mutableListOf<MainTab>()
    val appUiState: StateFlow<MusicAppUiState> = _appUiState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.uiState
    val equalizerState: StateFlow<EqualizerState> = equalizerManager.state
    private var youtubeSuggestionJob: Job? = null
    private var youtubeSearchJob: Job? = null
    private var youtubeLoadMoreJob: Job? = null
    private var selectedPlaylistSongsJob: Job? = null
    private val youtubeDownloadJobs = mutableMapOf<String, Job>()

    private fun reconcileDownloadedFavoriteState(songs: List<Song>, favorites: List<Song>): List<Song> {
        val favoriteVideoIds = favorites.mapNotNull { favorite ->
            com.example.data.YouTubeMusicService.videoIdFor(favorite)
                ?: favorite.takeIf { it.album == "YouTube Offline" }?.let { File(it.filePath).nameWithoutExtension }
        }.toSet()
        val onlineFavoriteStateById = songs.asSequence()
            .filter { it.album == "YouTube Online" || it.filePath.startsWith("yt://") }
            .mapNotNull { online -> com.example.data.YouTubeMusicService.videoIdFor(online)?.let { it to online.isFavorite } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, favoriteStates) -> favoriteStates.any { it } }
        return songs.map { candidate ->
            if (candidate.album != "YouTube Offline") candidate
            else {
                val videoId = File(candidate.filePath).nameWithoutExtension
                val isFavorite = when {
                    videoId in favoriteVideoIds -> true
                    videoId in onlineFavoriteStateById -> onlineFavoriteStateById[videoId] == true
                    else -> candidate.isFavorite
                }
                candidate.copy(isFavorite = isFavorite)
            }
        }
    }

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
        val savedAmbientModeName = repository.getPlayerAmbientMode()
        val savedAmbientMode = PlayerAmbientMode.entries.firstOrNull {
            it.name == savedAmbientModeName
        } ?: PlayerAmbientMode.OFF
        val savedAmbientStyle = PlayerAmbientStyle.entries.firstOrNull {
            it.name == repository.getPlayerAmbientStyle()
        } ?: PlayerAmbientStyle.GLOW
        val savedAmbientSpeed = repository.getPlayerAmbientSpeed()
        AppThemeColors.update(savedDarkTheme, Color(savedAccentColor))
        _appUiState.update {
            it.copy(
                geminiApiKey = savedKey,
                geminiModel = savedModel,
                isDarkTheme = savedDarkTheme,
                appearanceMode = savedAppearanceMode,
                accentColor = savedAccentColor,
                playerAmbientMode = savedAmbientMode,
                playerAmbientStyle = savedAmbientStyle,
                playerAmbientSpeed = savedAmbientSpeed,
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
                val downloadedVideoIds = songList
                    .filter { it.album == "YouTube Offline" && File(it.filePath).isFile }
                    .mapNotNull { File(it.filePath).nameWithoutExtension }
                    .toSet()
                _appUiState.update {
                    it.copy(
                        songs = reconcileDownloadedFavoriteState(songList, it.favoriteSongs),
                        downloadedYouTubeVideoIds = downloadedVideoIds
                    )
                }
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
                _appUiState.update {
                    it.copy(
                        favoriteSongs = favs,
                        songs = reconcileDownloadedFavoriteState(it.songs, favs)
                    )
                }
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
        updateTab(tab, rememberPrevious = true)
    }

    /** Bottom-navigation taps establish a new root instead of growing Back history. */
    fun selectMainTab(tab: MainTab) {
        tabBackStack.clear()
        updateTab(tab, rememberPrevious = false)
    }

    fun goBack() {
        val current = _appUiState.value.currentTab
        val previous = tabBackStack.removeLastOrNull() ?: MainTab.LIBRARY
        updateTab(if (previous == current) MainTab.LIBRARY else previous, rememberPrevious = false)
    }

    private fun updateTab(tab: MainTab, rememberPrevious: Boolean) {
        val current = _appUiState.value.currentTab
        if (current != tab && rememberPrevious) {
            tabBackStack.add(current)
            if (tabBackStack.size > 16) tabBackStack.removeAt(0)
        }
        _appUiState.update { it.copy(currentTab = tab) }
        val needsHighPrecision = (tab == MainTab.NOW_PLAYING || tab == MainTab.LYRICS)
        playerController.setHighPrecisionTracking(needsHighPrecision)
        if (tab == MainTab.YOUTUBE) {
            // The search response already supplies visitorData when available.
            // Avoid a separate network request competing with the first results page.
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

        if (query.trim().length < 2) return

        youtubeSuggestionJob = viewModelScope.launch {
            delay(280)
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
        youtubeSearchJob?.cancel()
        youtubeLoadMoreJob?.cancel()
        val category = _appUiState.value.selectedYouTubeCategory
        _appUiState.update {
            it.copy(
                youtubeSuggestions = emptyList(),
                isSearchingYouTube = true,
                isLoadingMoreYouTube = false,
                youtubeContinuation = null,
                youtubePaginationError = null,
                youtubeErrorMessage = null,
                youtubeSongs = emptyList(),
                youtubeQuery = trimmed
            )
        }
        youtubeSearchJob = viewModelScope.launch {
            try {
                val page = try {
                    com.example.data.YouTubeMusicService.searchSongsPage(trimmed)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    android.util.Log.e("MusicViewModel", "Error searching YouTube: ${t.message}", t)
                    com.example.data.YouTubeSearchPage(emptyList(), null)
                }
                _appUiState.update { current ->
                    if (current.youtubeQuery != trimmed || current.selectedYouTubeCategory != category) current
                    else current.copy(
                            isSearchingYouTube = false,
                            youtubeSongs = page.songs,
                            youtubeContinuation = page.continuation,
                            youtubeErrorMessage = if (page.songs.isEmpty()) "Không tìm thấy bài hát nào trên YouTube cho '$trimmed'" else null
                        )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.e("MusicViewModel", "Error in searchYouTube: ${t.message}", t)
                _appUiState.update { current ->
                    if (current.youtubeQuery != trimmed || current.selectedYouTubeCategory != category) current
                    else current.copy(
                        isSearchingYouTube = false,
                        youtubeErrorMessage = "Lỗi tìm kiếm YouTube: ${t.message}"
                    )
                }
            }
        }
    }

    fun selectYouTubeCategory(category: String) {
        youtubeSuggestionJob?.cancel()
        youtubeSearchJob?.cancel()
        youtubeLoadMoreJob?.cancel()
        _appUiState.update {
            it.copy(
                selectedYouTubeCategory = category,
                youtubeQuery = "",
                youtubeSuggestions = emptyList(),
                youtubeSongs = emptyList(),
                youtubeContinuation = null,
                youtubePaginationError = null,
                isLoadingMoreYouTube = false,
                youtubeErrorMessage = null,
                isSearchingYouTube = true
            )
        }
        val query = com.example.data.YouTubeMusicService.getCategoryQuery(category)
        youtubeSearchJob = viewModelScope.launch {
            try {
                val page = try {
                    com.example.data.YouTubeMusicService.searchSongsPage(query)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    android.util.Log.e("MusicViewModel", "Error fetching YouTube category songs: ${t.message}", t)
                    com.example.data.YouTubeSearchPage(emptyList(), null)
                }
                _appUiState.update { current ->
                    if (current.selectedYouTubeCategory != category || current.youtubeQuery.isNotBlank()) current
                    else current.copy(
                        isSearchingYouTube = false,
                        youtubeSongs = page.songs,
                        youtubeContinuation = page.continuation,
                        youtubeErrorMessage = if (page.songs.isEmpty()) "Không thể tải danh mục '$category' từ YouTube. Vui lòng thử lại." else null
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.e("MusicViewModel", "Error in selectYouTubeCategory: ${t.message}", t)
                _appUiState.update { current ->
                    if (current.selectedYouTubeCategory != category || current.youtubeQuery.isNotBlank()) current
                    else current.copy(
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
            playYouTubeQueue(ytSongs, index)
        } else {
            playYouTubeQueue(listOf(song), 0)
        }
    }

    /** Prefer the library's local copy for results that have already been downloaded. */
    fun playYouTubeQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        playQueue(songs, startIndex)
    }

    private fun resolveDownloadedYouTubeCopies(songs: List<Song>): List<Song> {
        val favoritedVideoIds = _appUiState.value.favoriteSongs.mapNotNull { favorite ->
            com.example.data.YouTubeMusicService.videoIdFor(favorite)
                ?: favorite.takeIf { it.album == "YouTube Offline" }?.let { File(it.filePath).nameWithoutExtension }
        }.toSet()
        val offlineByVideoId = _appUiState.value.songs
            .asSequence()
            .filter { it.album == "YouTube Offline" && File(it.filePath).isFile }
            .mapNotNull { local ->
                File(local.filePath).nameWithoutExtension
                    .takeIf(String::isNotBlank)
                    ?.let { videoId -> videoId to local }
            }
            .toMap()
        return songs.map { candidate ->
            val videoId = com.example.data.YouTubeMusicService.videoIdFor(candidate)
            val localCopy = videoId?.let(offlineByVideoId::get)
            if (localCopy == null) {
                candidate.copy(isFavorite = candidate.isFavorite || videoId?.let { it in favoritedVideoIds } == true)
            } else {
                localCopy.copy(isFavorite = candidate.isFavorite || localCopy.isFavorite || videoId?.let { it in favoritedVideoIds } == true)
            }
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

    fun toggleYouTubeSongDownload(song: Song, quality: YouTubeDownloadQuality = YouTubeDownloadQuality.HIGH_320) {
        val videoId = com.example.data.YouTubeMusicService.videoIdFor(song)
        if (videoId == null) {
            _appUiState.update { it.copy(scanResultMessage = "Không xác định được bài hát YouTube để tải.") }
            return
        }
        youtubeDownloadJobs[videoId]?.let {
            it.cancel()
            youtubeDownloadNotifier.cancel(videoId)
            return
        }
        if (videoId in _appUiState.value.downloadedYouTubeVideoIds) {
            _appUiState.update { it.copy(scanResultMessage = "Bài hát này đã có trong thư viện offline.") }
            return
        }

        _appUiState.update { current ->
            current.copy(
                youtubeDownloadProgress = current.youtubeDownloadProgress + (videoId to 0),
                youtubeDownloadBytes = current.youtubeDownloadBytes + (videoId to 0L),
                scanResultMessage = "Đang tải '${song.title}' từ YouTube…"
            )
        }
        val job = viewModelScope.launch {
            try {
                val downloaded = com.example.data.YouTubeAudioDownloader.download(
                    getApplication(),
                    song,
                    videoId,
                    quality.bitrateKbps
                ) { progress ->
                    youtubeDownloadNotifier.progress(videoId, song.title, progress.downloadedBytes, progress.totalBytes)
                    _appUiState.update { current ->
                        val previous = current.youtubeDownloadProgress[videoId] ?: return@update current
                        val percent = if (progress.totalBytes > 0L) {
                            (progress.downloadedBytes * 100L / progress.totalBytes).toInt().coerceIn(0, 99)
                        } else 0
                        current.copy(
                            youtubeDownloadProgress = current.youtubeDownloadProgress + (videoId to percent),
                            youtubeDownloadBytes = current.youtubeDownloadBytes + (videoId to progress.downloadedBytes)
                        )
                    }
                }
                val offlineSong = repository.addDownloadedYouTubeSong(song, downloaded.file, downloaded.format, downloaded.bitrateKbps)
                youtubeDownloadNotifier.finished(videoId, song.title)
                _appUiState.update { current ->
                    current.copy(
                        songs = (current.songs.filterNot { it.filePath == offlineSong.filePath } + offlineSong),
                        downloadedYouTubeVideoIds = current.downloadedYouTubeVideoIds + videoId,
                        scanResultMessage = "Đã tải '${song.title}' vào thư viện offline."
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                youtubeDownloadNotifier.cancel(videoId)
                _appUiState.update { it.copy(scanResultMessage = "Đã hủy tải '${song.title}'.") }
            } catch (error: Throwable) {
                youtubeDownloadNotifier.failed(videoId, song.title, error.message ?: "lỗi mạng")
                android.util.Log.e("MusicViewModel", "YouTube download failed for $videoId", error)
                _appUiState.update {
                    it.copy(scanResultMessage = "Không tải được '${song.title}': ${error.message ?: "lỗi mạng"}")
                }
            } finally {
                youtubeDownloadJobs.remove(videoId)
                _appUiState.update { current ->
                    current.copy(
                        youtubeDownloadProgress = current.youtubeDownloadProgress - videoId,
                        youtubeDownloadBytes = current.youtubeDownloadBytes - videoId
                    )
                }
            }
        }
        youtubeDownloadJobs[videoId] = job
    }

    fun loadMoreYouTube() {
        val snapshot = _appUiState.value
        val continuation = snapshot.youtubeContinuation ?: return
        if (snapshot.isSearchingYouTube || snapshot.isLoadingMoreYouTube || snapshot.youtubePaginationError != null) return
        val query = snapshot.youtubeQuery
        val category = snapshot.selectedYouTubeCategory
        youtubeLoadMoreJob?.cancel()
        youtubeLoadMoreJob = viewModelScope.launch {
            _appUiState.update { it.copy(isLoadingMoreYouTube = true) }
            try {
                val page = com.example.data.YouTubeMusicService.loadMoreSongs(continuation)
                _appUiState.update { current ->
                    if (current.youtubeContinuation != continuation || current.youtubeQuery != query || current.selectedYouTubeCategory != category) {
                        current.copy(isLoadingMoreYouTube = false)
                    } else {
                        val merged = (current.youtubeSongs + page.songs).distinctBy { it.filePath }
                        current.copy(
                            youtubeSongs = merged,
                            youtubeContinuation = page.continuation,
                            isLoadingMoreYouTube = false,
                            youtubePaginationError = if (page.songs.isEmpty() && page.continuation != null) {
                                "Không tải thêm được. Chạm để thử lại."
                            } else null
                        )
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                android.util.Log.w("MusicViewModel", "Error loading more YouTube results: ${error.message}", error)
                _appUiState.update { current ->
                    if (current.youtubeContinuation == continuation) {
                        current.copy(isLoadingMoreYouTube = false, youtubePaginationError = "Không tải thêm được. Chạm để thử lại.")
                    } else current.copy(isLoadingMoreYouTube = false)
                }
            }
        }
    }

    fun retryLoadMoreYouTube() {
        _appUiState.update { it.copy(youtubePaginationError = null) }
        loadMoreYouTube()
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
            val currentSong = playerState.value.currentSong
            val songVideoId = com.example.data.YouTubeMusicService.videoIdFor(song)
                ?: song.takeIf { it.album == "YouTube Offline" }?.let { File(it.filePath).nameWithoutExtension }
            val currentVideoId = currentSong?.let { current ->
                com.example.data.YouTubeMusicService.videoIdFor(current)
                    ?: current.takeIf { it.album == "YouTube Offline" }?.let { File(it.filePath).nameWithoutExtension }
            }
            val playerSongId = if (song.id > 0L) song.id else currentSong?.takeIf { songVideoId != null && songVideoId == currentVideoId }?.id ?: song.id
            playerController.updateFavoriteState(playerSongId, isFavorite)
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
        playerController.playAllSequential(resolveDownloadedYouTubeCopies(songs), startIndex)
    }

    fun playAllShuffled(songs: List<Song>) {
        clearPendingOnlineSelection()
        setTab(MainTab.NOW_PLAYING)
        playerController.playAllShuffled(resolveDownloadedYouTubeCopies(songs))
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        clearPendingOnlineSelection()
        setTab(MainTab.NOW_PLAYING)
        playerController.playQueue(resolveDownloadedYouTubeCopies(songs), startIndex)
    }

    /** Keep the playlist detail visible so playback can use the persistent mini-player. */
    fun playPlaylistQueue(songs: List<Song>, startIndex: Int = 0, playlistName: String? = null) {
        if (songs.isEmpty()) return
        clearPendingOnlineSelection()
        _appUiState.update { it.copy(currentTab = MainTab.LIBRARY) }
        playerController.playQueue(resolveDownloadedYouTubeCopies(songs), startIndex, playlistName)
    }

    fun playFavoriteQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val selectedIndex = startIndex.coerceIn(0, songs.lastIndex)
        val selectedSong = songs[selectedIndex]
        val resolvedSelectedSong = resolveDownloadedYouTubeCopies(listOf(selectedSong)).first()
        val isYouTubeFavorite = selectedSong.filePath.startsWith("yt://") ||
            selectedSong.album.equals("YouTube Online", ignoreCase = true) ||
            selectedSong.format.contains("YouTube", ignoreCase = true) ||
            com.example.data.YouTubeMusicService.videoIdFor(selectedSong) != null

        clearPendingOnlineSelection()
        if (!isYouTubeFavorite || resolvedSelectedSong.album == "YouTube Offline") {
            playQueue(songs, selectedIndex)
            return
        }

        // Publish a screen-level loading selection before entering player cleanup/resolution.
        // That work can block on some devices, so the player screen must not depend on the
        // controller's currentSong changing first.
        setTab(MainTab.NOW_PLAYING)
        _appUiState.update {
            it.copy(currentTab = MainTab.NOW_PLAYING, pendingOnlineSong = selectedSong)
        }
        pendingOnlinePlaybackJob = viewModelScope.launch {
            try {
                // Give Compose a frame to show the pending song before MediaPlayer teardown.
                delay(64)
                playerController.playQueue(resolveDownloadedYouTubeCopies(songs), selectedIndex)
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
        playerController.playSong(resolveDownloadedYouTubeCopies(listOf(song)).first())
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
        val styles = VisualizerStyle.entries
        val nextIndex = (styles.indexOf(_appUiState.value.visualizerStyle) + 1) % styles.size
        setVisualizerStyle(styles[nextIndex])
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        _appUiState.update { it.copy(visualizerStyle = style) }
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

    fun setShowDeleteSong(song: Song?) {
        _appUiState.update { it.copy(showDeleteSongDialog = song) }
    }

    fun deleteSong(song: Song) {
        _appUiState.update { it.copy(showDeleteSongDialog = null) }
        viewModelScope.launch {
            try {
                // Close the player before deleting a file it may currently be reading.
                playerController.removeSongFromQueue(song)
                val deletedFile = repository.deleteSongFromLibrary(song)
                val recent = _appUiState.value.recentlyPlayedSongs.filterNot {
                    (song.id > 0L && it.id == song.id) || it.filePath == song.filePath
                }
                repository.replaceRecentlyPlayedSongs(recent)
                _appUiState.update { state ->
                    state.copy(
                        showDeleteSongDialog = null,
                        recentlyPlayedSongs = recent,
                        selectedFolder = state.selectedFolder?.let { folder ->
                            folder.copy(songs = folder.songs.filterNot {
                                (song.id > 0L && it.id == song.id) || it.filePath == song.filePath
                            })
                        },
                        pendingOnlineSong = state.pendingOnlineSong?.takeUnless { it.filePath == song.filePath },
                        scanResultMessage = when {
                            deletedFile -> "Đã xóa tệp '${song.title}' khỏi thiết bị."
                            song.filePath.startsWith("yt://") -> "Đã xóa '${song.title}' khỏi thư viện."
                            else -> "Đã gỡ '${song.title}' khỏi thư viện. Tệp gốc vẫn còn trên thiết bị."
                        }
                    )
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                android.util.Log.e("MusicViewModel", "Unable to delete song", error)
                _appUiState.update {
                    it.copy(showDeleteSongDialog = null, scanResultMessage =
                        "Không xóa được '${song.title}': ${error.message ?: "lỗi dữ liệu"}")
                }
            }
        }
    }

    fun setShowRemoveFromPlaylist(song: Song?) {
        _appUiState.update { it.copy(showRemoveFromPlaylistDialog = song) }
    }

    fun removeSongFromPlaylist(playlistId: Long, song: Song) {
        _appUiState.update { it.copy(showRemoveFromPlaylistDialog = null) }
        viewModelScope.launch {
            try {
                repository.removeSongFromPlaylist(playlistId, song.id)
                _appUiState.update {
                    it.copy(showRemoveFromPlaylistDialog = null,
                        scanResultMessage = "Đã xóa '${song.title}' khỏi danh sách phát.")
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _appUiState.update {
                    it.copy(showRemoveFromPlaylistDialog = null,
                        scanResultMessage = "Không xóa được bài hát khỏi danh sách phát.")
                }
            }
        }
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
        if (trimmed.isBlank()) repository.setGeminiModel("gemini-3.8-flash")
        _appUiState.update {
            it.copy(
                geminiApiKey = repository.getGeminiApiKey(),
                geminiModel = if (trimmed.isBlank()) "gemini-3.8-flash" else it.geminiModel,
                scanResultMessage = if (trimmed.isBlank()) "Đã chuyển sang API Key tích hợp" else "Đã lưu Gemini API Key riêng"
            )
        }
    }

    fun setGeminiModel(model: String) {
        repository.setGeminiModel(model)
        _appUiState.update { it.copy(geminiModel = model, scanResultMessage = "Đã chọn mô hình: $model") }
    }

    fun searchLyricsOnline(song: Song, isAuto: Boolean = false, useGemini: Boolean = false) {
        viewModelScope.launch {
            _appUiState.update {
                it.copy(
                    isSearchingLyrics = true,
                    geminiLyricsSuggestionSongId = if (useGemini) null else it.geminiLyricsSuggestionSongId
                )
            }
            val currentAppState = _appUiState.value
            val result = try {
                val videoId = if (useGemini) {
                    Regex("(?:/vi/|/v/|[?&]v=|yt://)([A-Za-z0-9_-]{11})")
                        .find("${song.filePath} ${song.albumArtUri.orEmpty()}")?.groupValues?.getOrNull(1)
                } else null
                com.example.lyrics.OnlineLyricsService.fetchLyrics(
                    rawTitle = song.title,
                    rawArtist = song.artist,
                    durationMs = song.durationMs,
                    apiKey = currentAppState.geminiApiKey,
                    preferredModel = currentAppState.geminiModel,
                    useGemini = useGemini,
                    youtubeVideoId = videoId,
                    localAudioPath = if (useGemini && videoId == null &&
                        (song.filePath.startsWith("/") || song.filePath.startsWith("content://") || song.filePath.startsWith("file://")))
                        song.filePath else null,
                    localAudioFormat = song.format,
                    audioContext = if (useGemini) getApplication<Application>() else null
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.w("MusicViewModel", "Lyrics lookup failed for '${song.title}': ${t.message}", t)
                null
            }
            _appUiState.update { it.copy(isSearchingLyrics = false) }

            if (result != null && result.bestLyrics != null) {
                val lyricsBody = result.bestLyrics!!
                val lyricsText = buildList {
                    add("[source:${result.sourceName}]")
                    result.sourceUrls.take(4).filter { it.startsWith("https://") }.forEach { add("[sourceurl:${it.replace("]", "")}]" ) }
                    result.timingSource?.let { add("[timing:$it]") }
                    add(lyricsBody.trim())
                }.joinToString("\n")
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
                val formatType = if (!result.syncedLyrics.isNullOrBlank()) "Karaoke LRC đồng bộ" else "lời văn bản chưa có mốc thời gian"
                val prefix = if (isAuto) "Tự động tải lời" else "Đã tìm thấy lời"
                val sourceLabel = if (result.sourceName == "Gemini AI") {
                    if (result.sourceUrls.isEmpty()) "Gemini AI · lời tham khảo" else "Gemini AI · có nguồn tra cứu"
                } else "LRCLIB"
                _appUiState.update { it.copy(scanResultMessage = "$prefix $formatType từ $sourceLabel cho: ${song.title}", geminiLyricsSuggestionSongId = null) }
            } else if (!useGemini) {
                _appUiState.update {
                    it.copy(
                        geminiLyricsSuggestionSongId = if (playerState.value.currentSong?.id == song.id) song.id else it.geminiLyricsSuggestionSongId,
                        scanResultMessage = if (isAuto) it.scanResultMessage else "Không tìm thấy lời khớp trên LRCLIB. Bạn có thể thử Gemini AI cho: ${song.title}"
                    )
                }
            } else if (!isAuto) {
                _appUiState.update { it.copy(scanResultMessage = "Gemini chưa tạo được lời cho: ${song.title}") }
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

    fun setPlayerAmbientMode(mode: PlayerAmbientMode) {
        repository.setPlayerAmbientMode(mode.name)
        _appUiState.update { it.copy(playerAmbientMode = mode) }
    }

    fun setPlayerAmbientStyle(style: PlayerAmbientStyle) {
        repository.setPlayerAmbientStyle(style.name)
        _appUiState.update { it.copy(playerAmbientStyle = style) }
    }

    fun setPlayerAmbientSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 2f)
        repository.setPlayerAmbientSpeed(clamped)
        _appUiState.update { it.copy(playerAmbientSpeed = clamped) }
    }

    private fun lyricsTrackKey(song: Song): String = "${song.id}|${song.title}|${song.artist}"

    private fun isSameLyricsTrack(first: Song, second: Song): Boolean =
        first.filePath == second.filePath ||
            (first.id == second.id && first.title == second.title && first.artist == second.artist)

    fun alignLyricsWithGemini(rawLyrics: String, onComplete: ((String?) -> Unit)? = null) {
        val currentSong = playerState.value.currentSong ?: return
        if (rawLyrics.isBlank()) {
            _appUiState.update { it.copy(scanResultMessage = "Hãy tải hoặc nhập lời gốc trước khi căn mốc thời gian") }
            onComplete?.invoke(null)
            return
        }
        viewModelScope.launch {
            _appUiState.update { it.copy(isAligningLyrics = true) }
            val apiKey = _appUiState.value.geminiApiKey
            val preferredModel = _appUiState.value.geminiModel
            val alignedLrc = try {
                com.example.lyrics.OnlineLyricsService.alignLyricsWithGemini(
                    plainLyrics = rawLyrics,
                    cleanTitle = com.example.lyrics.OnlineLyricsService.cleanSearchTerm(currentSong.title),
                    cleanArtist = com.example.lyrics.OnlineLyricsService.cleanSearchTerm(currentSong.artist),
                    durationMs = currentSong.durationMs,
                    apiKey = apiKey,
                    preferredModel = preferredModel
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                android.util.Log.w("MusicViewModel", "Lyric alignment failed: ${error.message}", error)
                null
            }
            _appUiState.update { it.copy(isAligningLyrics = false) }
            if (!alignedLrc.isNullOrBlank()) {
                val provenance = rawLyrics.lines().filter {
                    it.trim().matches(Regex("(?i)^\\[(source|sourceurl):[^\\]]*]$"))
                }
                val timedLyrics = (provenance + "[timing:Gemini AI (ước tính)]" + alignedLrc).joinToString("\n")
                updateLyrics(currentSong.id, timedLyrics, 0)
                _appUiState.update {
                    it.copy(scanResultMessage = "Đã giữ nguyên lời và gắn mốc thời gian AI ước tính. Có thể chỉnh độ lệch nếu cần.")
                }
                onComplete?.invoke(timedLyrics)
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
        selectedPlaylistSongsJob?.cancel()
        _appUiState.update {
            it.copy(
                selectedPlaylist = playlist,
                selectedPlaylistSongs = emptyList(),
                showRemoveFromPlaylistDialog = null
            )
        }
        selectedPlaylistSongsJob = viewModelScope.launch {
            repository.getSongsForPlaylist(playlist.id).collect { songs ->
                _appUiState.update { current ->
                    if (current.selectedPlaylist?.id == playlist.id) {
                        current.copy(selectedPlaylistSongs = songs)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun closePlaylist() {
        selectedPlaylistSongsJob?.cancel()
        selectedPlaylistSongsJob = null
        _appUiState.update {
            it.copy(selectedPlaylist = null, selectedPlaylistSongs = emptyList(), showRemoveFromPlaylistDialog = null)
        }
    }

    fun createPlaylist(name: String, desc: String) {
        viewModelScope.launch {
            try {
                val playlistId = repository.createPlaylist(name, desc)
                val pendingSong = _appUiState.value.pendingPlaylistSongForCreation
                if (pendingSong != null) repository.addSongToPlaylist(playlistId, pendingSong)
                _appUiState.update {
                    it.copy(
                        showCreatePlaylistDialog = false,
                        pendingPlaylistSongForCreation = null,
                        scanResultMessage = pendingSong?.let { song -> "Đã tạo danh sách và thêm '${song.title}'." }
                    )
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                android.util.Log.e("MusicViewModel", "Unable to create playlist", error)
                _appUiState.update { it.copy(scanResultMessage = "Không thể tạo danh sách phát: ${error.message ?: "lỗi dữ liệu"}") }
            }
        }
    }

    fun createPlaylistForSong(song: Song) {
        _appUiState.update {
            it.copy(
                showAddToPlaylistDialog = null,
                showCreatePlaylistDialog = true,
                pendingPlaylistSongForCreation = song
            )
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            _appUiState.update { it.copy(showAddToPlaylistDialog = null) }
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            try {
                repository.addSongToPlaylist(playlistId, song)
                _appUiState.update {
                    it.copy(
                        showAddToPlaylistDialog = null,
                        scanResultMessage = "Đã thêm '${song.title}' vào danh sách phát."
                    )
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                android.util.Log.e("MusicViewModel", "Unable to add song to playlist", error)
                _appUiState.update {
                    it.copy(scanResultMessage = "Không thể thêm '${song.title}' vào danh sách phát: ${error.message ?: "lỗi dữ liệu"}")
                }
            }
        }
    }

    fun setShowCreatePlaylist(show: Boolean) = _appUiState.update {
        it.copy(
            showCreatePlaylistDialog = show,
            pendingPlaylistSongForCreation = if (show) it.pendingPlaylistSongForCreation else null
        )
    }
    fun setShowAddToPlaylist(song: Song?) = _appUiState.update { it.copy(showAddToPlaylistDialog = song) }
    fun setShowEditLyrics(show: Boolean) = _appUiState.update { it.copy(showEditLyricsDialog = show) }
    fun setShowSleepTimer(show: Boolean) = _appUiState.update { it.copy(showSleepTimerDialog = show) }
    fun setShowSavePreset(show: Boolean) = _appUiState.update { it.copy(showSavePresetDialog = show) }
    fun setShowAudioSpecs(show: Boolean) = _appUiState.update { it.copy(showAudioSpecsDialog = show) }

    fun getFilteredSongs(): List<Song> {
        val query = _appUiState.value.searchQuery.trim().lowercase()
        val favoriteVideoIds = _appUiState.value.favoriteSongs.mapNotNull { favorite ->
            com.example.data.YouTubeMusicService.videoIdFor(favorite)
                ?: favorite.takeIf { it.album == "YouTube Offline" }?.let { File(it.filePath).nameWithoutExtension }
        }.toSet()
        val all = when (_appUiState.value.librarySubTab) {
            LibrarySubTab.ALL_SONGS -> _appUiState.value.songs.filterNot { song ->
                song.isFavorite || (song.album == "YouTube Offline" &&
                    File(song.filePath).nameWithoutExtension in favoriteVideoIds)
            }
            LibrarySubTab.FOLDERS -> _appUiState.value.songs
            LibrarySubTab.FAVORITES -> _appUiState.value.favoriteSongs
            LibrarySubTab.HI_RES -> _appUiState.value.songs.filter { it.isHiRes }
            LibrarySubTab.RECENT -> _appUiState.value.recentlyPlayedSongs
            LibrarySubTab.YOUTUBE_DOWNLOADS -> _appUiState.value.songs.filter { it.album == "YouTube Offline" }
            LibrarySubTab.PLAYLISTS -> _appUiState.value.songs
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
