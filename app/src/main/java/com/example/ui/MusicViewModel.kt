package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.equalizer.EqualizerBand
import com.example.equalizer.EqualizerManager
import com.example.equalizer.EqualizerState
import com.example.equalizer.SpeakerProfile
import com.example.lyrics.LrcParser
import com.example.lyrics.ParsedLyrics
import com.example.model.EqualizerPreset
import com.example.model.Playlist
import com.example.model.Song
import com.example.player.MusicPlayerController
import com.example.player.PlayerUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MainTab(val title: String) {
    LIBRARY("Thư viện"),
    NOW_PLAYING("Đang phát"),
    LYRICS("Lời bài hát"),
    EQUALIZER("Bộ chỉnh âm"),
    SETTINGS("Cài đặt")
}

enum class LibrarySubTab(val title: String) {
    ALL_SONGS("Tất cả"),
    FAVORITES("Yêu thích"),
    PLAYLISTS("Danh sách phát"),
    HI_RES("Hi-Res FLAC/WAV"),
    ARTISTS("Nghệ sĩ")
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
    val presets: List<EqualizerPreset> = emptyList(),
    val parsedLyrics: ParsedLyrics = ParsedLyrics(),
    val activeLyricIndex: Int = -1,
    val isScanning: Boolean = false,
    val scanResultMessage: String? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val showAddToPlaylistDialog: Song? = null,
    val showEditLyricsDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showSavePresetDialog: Boolean = false,
    val showAudioSpecsDialog: Boolean = false
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
        viewModelScope.launch {
            repository.initializeDefaultDataIfEmpty()
        }

        viewModelScope.launch {
            repository.allSongs.collect { songList ->
                _appUiState.update { it.copy(songs = songList) }
                // If no song is loaded in player, load the first one ready
                if (playerController.uiState.value.currentSong == null && songList.isNotEmpty()) {
                    playerController.playQueue(songList.take(1), 0)
                    playerController.togglePlayPause() // Keep paused initially
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
        viewModelScope.launch {
            playerState.collect { pState ->
                val song = pState.currentSong
                if (song != null) {
                    val parsed = LrcParser.parse(song.lyrics, song.lrcOffsetMs)
                    val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, pState.currentPositionMs)
                    _appUiState.update {
                        it.copy(
                            parsedLyrics = parsed,
                            activeLyricIndex = activeIdx
                        )
                    }
                }
            }
        }
    }

    fun setTab(tab: MainTab) {
        _appUiState.update { it.copy(currentTab = tab) }
    }

    fun setLibrarySubTab(subTab: LibrarySubTab) {
        _appUiState.update { it.copy(librarySubTab = subTab) }
    }

    fun setSearchQuery(query: String) {
        _appUiState.update { it.copy(searchQuery = query) }
    }

    fun playSong(song: Song) {
        val currentList = getFilteredSongs()
        val index = currentList.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }
        playerController.playQueue(if (currentList.isNotEmpty()) currentList else listOf(song), index)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        playerController.playQueue(songs, startIndex)
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun playNext() = playerController.playNext()
    fun playPrevious() = playerController.playPrevious()
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeatMode() = playerController.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)
    fun startSleepTimer(minutes: Int) = playerController.startSleepTimer(minutes)
    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    fun scanDeviceAudio() {
        viewModelScope.launch {
            _appUiState.update { it.copy(isScanning = true, scanResultMessage = null) }
            val count = repository.scanDeviceAudioFiles()
            _appUiState.update {
                it.copy(
                    isScanning = false,
                    scanResultMessage = if (count > 0) "Đã quét và thêm $count bài hát từ thiết bị!" else "Không tìm thấy file nhạc mới hoặc đã cập nhật đủ."
                )
            }
        }
    }

    fun dismissScanMessage() {
        _appUiState.update { it.copy(scanResultMessage = null) }
    }

    // Equalizer controls
    fun toggleEqualizerEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)
    fun toggleSystemWideEq(enabled: Boolean) = equalizerManager.toggleSystemWide(enabled)
    fun updateBandLevel(bandIndex: Short, levelMb: Short) = equalizerManager.updateBandLevel(bandIndex, levelMb)
    fun setBassBoost(strength: Int) = equalizerManager.setBassBoost(strength)
    fun setVirtualizer(strength: Int) = equalizerManager.setVirtualizer(strength)
    fun setLoudnessEnhancer(gainMb: Int) = equalizerManager.setLoudnessEnhancerGain(gainMb)
    fun toggleAntiClipping(enabled: Boolean) = equalizerManager.toggleAntiClipping(enabled)
    fun setSpeakerProfile(profile: SpeakerProfile) = equalizerManager.setSpeakerProfile(profile)
    fun setReverbPreset(preset: Short) = equalizerManager.setReverbPreset(preset)
    fun toggleHiResDsp(enabled: Boolean) = equalizerManager.toggleHiResDsp(enabled)
    fun toggleReplayGain(enabled: Boolean) = equalizerManager.toggleReplayGain(enabled)
    fun refreshAudioDevice() = equalizerManager.detectCurrentAudioDevice()

    fun applyPreset(preset: EqualizerPreset) {
        val gains = preset.bandLevelsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        equalizerManager.applyPreset(preset.name, gains, preset.bassBoost, preset.virtualizer)
        equalizerManager.setReverbPreset(preset.reverbPreset.toShort())
    }

    fun saveCurrentPreset(name: String) {
        val currentBands = equalizerState.value.bands.joinToString(",") { it.levelMilliBels.toString() }
        val preset = EqualizerPreset(
            name = name,
            isCustom = true,
            bandLevelsCsv = currentBands,
            bassBoost = equalizerState.value.bassBoostStrength,
            virtualizer = equalizerState.value.virtualizerStrength,
            reverbPreset = equalizerState.value.reverbPreset.toInt()
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
            _appUiState.update { it.copy(showEditLyricsDialog = false) }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        val song = playerState.value.currentSong ?: return
        val newOffset = song.lrcOffsetMs + deltaMs
        viewModelScope.launch {
            repository.updateSongLyrics(song.id, song.lyrics ?: "", newOffset)
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
