package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.MainTab
import com.example.ui.MusicViewModel
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MusicViewModel) {
    val uiState by viewModel.appUiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val equalizerState by viewModel.equalizerState.collectAsState()

    // Back button handling: return to Library tab if on another tab and no playlist is open
    BackHandler(enabled = uiState.currentTab != MainTab.LIBRARY && uiState.selectedPlaylist == null) {
        viewModel.setTab(MainTab.LIBRARY)
    }

    // Permission request for audio storage
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.scanDeviceAudioIfEmpty()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        topBar = {
            if (uiState.currentTab != MainTab.NOW_PLAYING && uiState.selectedPlaylist == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NeonViolet.copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.8f)),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.Headphones, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Music EQ",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Hi-Res Audio & Equalizer",
                                    fontSize = 10.sp,
                                    color = TextHighlight,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        // Hi-Res Lossless badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = HiResGold.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HiResGold)
                        ) {
                            Text(
                                text = "LOSSLESS",
                                color = HiResGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Quick Equalizer Button
                        IconButton(
                            onClick = { viewModel.setTab(MainTab.EQUALIZER) },
                            modifier = Modifier.testTag("top_app_bar_eq_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Bộ chỉnh âm",
                                tint = if (equalizerState.isEnabled) NeonCyan else TextMuted
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground,
                        titleContentColor = TextPrimary
                    )
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Persistent Floating MiniPlayer above Bottom Navigation when not on NOW_PLAYING screen
                if (uiState.currentTab != MainTab.NOW_PLAYING && playerState.currentSong != null) {
                    MiniPlayerBar(
                        playerState = playerState,
                        onBarClick = { viewModel.setTab(MainTab.NOW_PLAYING) },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.playNext() },
                        onFavoriteClick = { viewModel.toggleFavorite(it) }
                    )
                }

                // Bottom Navigation Bar with responsive typography & compact paddings
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    val tabs = listOf(
                        Triple(MainTab.LIBRARY, Icons.Default.LibraryMusic, "Thư viện"),
                        Triple(MainTab.NOW_PLAYING, Icons.Default.PlayCircleFilled, "Đang phát"),
                        Triple(MainTab.LYRICS, Icons.Default.Lyrics, "Lời nhạc"),
                        Triple(MainTab.EQUALIZER, Icons.Default.Tune, "Bộ EQ"),
                        Triple(MainTab.SETTINGS, Icons.Default.Settings, "Cài đặt")
                    )

                    tabs.forEach { (tab, icon, label) ->
                        val isSelected = uiState.currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (uiState.selectedPlaylist != null) {
                                    viewModel.closePlaylist()
                                }
                                viewModel.setTab(tab)
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) NeonCyan else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    color = if (isSelected) NeonCyan else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = NeonCyan.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.selectedPlaylist != null -> {
                    PlaylistDetailScreen(
                        playlist = uiState.selectedPlaylist!!,
                        songs = uiState.selectedPlaylistSongs,
                        viewModel = viewModel,
                        playerState = playerState,
                        onBack = { viewModel.closePlaylist() }
                    )
                }
                uiState.currentTab == MainTab.LIBRARY -> {
                    LibraryScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        playerState = playerState
                    )
                }
                uiState.currentTab == MainTab.NOW_PLAYING -> {
                    NowPlayingScreen(
                        viewModel = viewModel,
                        playerState = playerState
                    )
                }
                uiState.currentTab == MainTab.LYRICS -> {
                    SyncedLyricsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        playerState = playerState
                    )
                }
                uiState.currentTab == MainTab.EQUALIZER -> {
                    EqualizerScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        playerState = playerState,
                        equalizerState = equalizerState
                    )
                }
                uiState.currentTab == MainTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        playerState = playerState,
                        equalizerState = equalizerState
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.setShowCreatePlaylist(false) },
            onConfirm = { name, desc -> viewModel.createPlaylist(name, desc) }
        )
    }

    uiState.showAddToPlaylistDialog?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = uiState.playlists,
            onDismiss = { viewModel.setShowAddToPlaylist(null) },
            onSelectPlaylist = { playlist -> viewModel.addSongToPlaylist(playlist.id, song.id) },
            onCreateNewPlaylist = {
                viewModel.setShowAddToPlaylist(null)
                viewModel.setShowCreatePlaylist(true)
            }
        )
    }

    if (uiState.showEditLyricsDialog) {
        playerState.currentSong?.let { song ->
            EditLyricsDialog(
                song = song,
                isAligning = uiState.isAligningLyrics,
                onDismiss = { viewModel.setShowEditLyrics(false) },
                onSave = { lrcText, offset -> viewModel.updateLyrics(song.id, lrcText, offset) },
                onAlignLyrics = { rawLyrics -> viewModel.alignLyricsWithGemini(rawLyrics) }
            )
        }
    }

    if (uiState.showSleepTimerDialog) {
        SleepTimerDialog(
            currentRemainingSec = playerState.sleepTimerRemainingSeconds,
            isTimerActive = playerState.isSleepTimerActive,
            onDismiss = { viewModel.setShowSleepTimer(false) },
            onSelectMinutes = { mins -> viewModel.startSleepTimer(mins) },
            onCancelTimer = { viewModel.cancelSleepTimer() }
        )
    }

    if (uiState.showSavePresetDialog) {
        SavePresetDialog(
            onDismiss = { viewModel.setShowSavePreset(false) },
            onSave = { name -> viewModel.saveCurrentPreset(name) }
        )
    }

    if (uiState.showAudioSpecsDialog) {
        AudioSpecsDialog(
            song = playerState.currentSong,
            onDismiss = { viewModel.setShowAudioSpecs(false) }
        )
    }

    uiState.showEditMetadataDialog?.let { song ->
        EditSongMetadataDialog(
            song = song,
            onDismiss = { viewModel.setShowEditMetadata(null) },
            onSave = { title, artist, album, format ->
                viewModel.saveSongMetadata(song.id, title, artist, album, format)
            }
        )
    }
}
