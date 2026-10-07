package com.example

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.MainTab
import com.example.ui.MusicViewModel
import com.example.ui.YouTubeDownloadQuality
import com.example.model.Song
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by viewModel.appUiState.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()
            val resolvedDarkTheme = when (appearance.appearanceMode) {
                "DARK" -> true
                "SYSTEM" -> systemDarkTheme
                else -> false
            }
            LaunchedEffect(resolvedDarkTheme, appearance.appearanceMode) {
                viewModel.applyResolvedAppearance(resolvedDarkTheme)
            }
            MyApplicationTheme(
                darkTheme = resolvedDarkTheme,
                accentColor = Color(appearance.accentColor)
            ) {
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
    val snackbarHostState = remember { SnackbarHostState() }
    val screenStateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current
    var pendingYouTubeDownload by remember { mutableStateOf<Pair<Song, YouTubeDownloadQuality>?>(null) }
    var qualityPickerSong by remember { mutableStateOf<Song?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> pendingYouTubeDownload?.let { (song, quality) -> viewModel.toggleYouTubeSongDownload(song, quality) }; pendingYouTubeDownload = null }
    val startYouTubeDownload: (Song, YouTubeDownloadQuality) -> Unit = { song, quality ->
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingYouTubeDownload = song to quality
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.toggleYouTubeSongDownload(song, quality)
    }
    val requestYouTubeDownload: (Song) -> Unit = { song ->
        val videoId = com.example.data.YouTubeMusicService.videoIdFor(song)
        if (videoId != null && videoId in uiState.youtubeDownloadProgress) {
            viewModel.toggleYouTubeSongDownload(song)
        } else {
            qualityPickerSong = song
        }
    }

    LaunchedEffect(uiState.scanResultMessage) {
        uiState.scanResultMessage?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
        }
    }

    // Keep a single tab-aware Back handler so playback returns to its actual source screen.
    BackHandler(enabled = uiState.currentTab != MainTab.LIBRARY && uiState.selectedPlaylist == null) {
        viewModel.goBack()
    }

    val visualizerPermission = Manifest.permission.RECORD_AUDIO
    var visualizerPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, visualizerPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var showVisualizerPermissionDialog by remember { mutableStateOf(false) }
    var visualizerPermissionPromptShown by remember { mutableStateOf(false) }
    val visualizerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> visualizerPermissionGranted = granted }

    LaunchedEffect(uiState.currentTab, playerState.isPlaying) {
        if (
            uiState.currentTab == MainTab.NOW_PLAYING && playerState.isPlaying &&
            !visualizerPermissionPromptShown &&
            ContextCompat.checkSelfPermission(context, visualizerPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            showVisualizerPermissionDialog = true
            visualizerPermissionPromptShown = true
        }
    }

    if (showVisualizerPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showVisualizerPermissionDialog = false
            },
            title = { Text("Đồng bộ sóng nhạc") },
            text = { Text("Android cần quyền âm thanh để phân tích tín hiệu bài đang phát và đồng bộ sóng nhạc. Ứng dụng chỉ dùng tín hiệu này để hiển thị trực tiếp trên thiết bị.") },
            confirmButton = {
                TextButton(onClick = {
                    showVisualizerPermissionDialog = false
                    visualizerPermissionLauncher.launch(visualizerPermission)
                }) { Text("Tiếp tục") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showVisualizerPermissionDialog = false
                }) { Text("Để sau") }
            }
        )
    }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Ask for audio access only when the user chooses to scan.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[audioPermission] == true) {
            viewModel.scanDeviceAudio()
        }
    }

    val requestMusicScan = {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanDeviceAudio()
        } else {
            permissionLauncher.launch(arrayOf(audioPermission))
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { folderUri ->
        if (folderUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.scanAudioFolder(folderUri)
        }
    }
    val pickAudioFolder: (String?) -> Unit = { suggestedFolder ->
        val initialUri = suggestedFolder?.let { name ->
            android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$name"
            )
        }
        folderPicker.launch(initialUri)
    }

    if (uiState.showInitialScanRecommendation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInitialScanRecommendation() },
            title = { Text("Quét nhạc trên thiết bị") },
            text = { Text("Để bắt đầu nhanh, bạn có thể quét các bài hát đang có trên điện thoại và thêm chúng vào thư viện.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissInitialScanRecommendation()
                    requestMusicScan()
                }) { Text("Quét nhạc") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInitialScanRecommendation() }) { Text("Để sau") }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackgroundBrush),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkSurfaceElevated,
                    contentColor = TextPrimary,
                    actionColor = NeonCyan
                )
            }
        },
        topBar = {
            if (uiState.currentTab in setOf(MainTab.LIBRARY, MainTab.YOUTUBE, MainTab.SETTINGS) && uiState.selectedPlaylist == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isLibraryTab = uiState.currentTab == MainTab.LIBRARY
                            val headerIcon = when (uiState.currentTab) {
                                MainTab.YOUTUBE -> Icons.Default.PlayArrow
                                MainTab.SETTINGS -> Icons.Default.Settings
                                else -> Icons.Default.MusicNote
                            }
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = if (uiState.currentTab == MainTab.YOUTUBE) Color(0xFFFF0033) else NeonViolet.copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.currentTab == MainTab.YOUTUBE) Color(0xFFFF6278) else NeonCyan.copy(alpha = 0.8f)),
                                modifier = Modifier.size(if (isLibraryTab) 38.dp else 34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = headerIcon,
                                        contentDescription = null,
                                        tint = if (uiState.currentTab == MainTab.YOUTUBE) Color.White else NeonCyan,
                                        modifier = Modifier.size(if (isLibraryTab) 23.dp else 20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            if (isLibraryTab) {
                                Text(
                                    text = "Music EQ",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            } else {
                                Column {
                                    Text(
                                        text = if (uiState.currentTab == MainTab.YOUTUBE) "YouTube" else "Cài đặt",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (uiState.currentTab == MainTab.YOUTUBE) "Khám phá nhạc trực tuyến" else "Tùy chỉnh trải nghiệm nghe nhạc",
                                        fontSize = 10.sp,
                                        color = TextHighlight,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // Keep the equalizer one tap away with the compact control shown in the library mockup.
                        val isEqActive = uiState.currentTab == MainTab.EQUALIZER || equalizerState.isEnabled
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isEqActive) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isEqActive) NeonCyan else DarkBorder
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (uiState.selectedPlaylist != null) {
                                        viewModel.closePlaylist()
                                    }
                                    viewModel.setTab(MainTab.EQUALIZER)
                                }
                                .testTag("top_app_bar_eq_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Bộ EQ",
                                    tint = if (isEqActive) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary
                    )
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (playerState.isLoadingOnlineStream) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = "Đang tải dữ liệu bài hát…",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Persistent Floating MiniPlayer above Bottom Navigation when not on NOW_PLAYING screen
                if (uiState.currentTab !in setOf(MainTab.NOW_PLAYING, MainTab.EQUALIZER) && playerState.currentSong != null) {
                    MiniPlayerBar(
                        playerState = playerState,
                        onBarClick = {
                            if (uiState.selectedPlaylist != null) viewModel.closePlaylist()
                            viewModel.setTab(MainTab.NOW_PLAYING)
                        },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.playNext() },
                        onFavoriteClick = { viewModel.toggleFavorite(it) }
                    )
                }

                // Bottom Navigation Bar with responsive typography & compact paddings
                if (uiState.currentTab !in setOf(MainTab.NOW_PLAYING, MainTab.EQUALIZER)) NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    val tabs = listOf(
                        Triple(MainTab.LIBRARY, Icons.Default.LibraryMusic, "Thư viện"),
                        Triple(MainTab.YOUTUBE, Icons.Default.Subscriptions, "YouTube"),
                        Triple(MainTab.SETTINGS, Icons.Default.Settings, "Cài đặt")
                    )

                    tabs.forEach { (tab, icon, label) ->
                        val isSelected = uiState.currentTab == tab
                        val isYt = tab == MainTab.YOUTUBE
                        val activeColor = if (isYt) Color(0xFFFF0033) else NeonCyan

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (uiState.selectedPlaylist != null) {
                                    viewModel.closePlaylist()
                                }
                                viewModel.selectMainTab(tab)
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) activeColor else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    color = if (isSelected) activeColor else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = activeColor.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val screenKey = uiState.selectedPlaylist?.let { "playlist_${it.id}" } ?: uiState.currentTab.name
            screenStateHolder.SaveableStateProvider(screenKey) {
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
                            playerState = playerState,
                            onRequestScan = requestMusicScan,
                            onPickAudioFolder = { pickAudioFolder(null) },
                            onPickSuggestedFolder = pickAudioFolder
                        )
                    }
                    uiState.currentTab == MainTab.YOUTUBE -> {
                        YouTubeOnlineScreen(
                            viewModel = viewModel,
                            uiState = uiState,
                            playerState = playerState,
                            onDownloadSong = requestYouTubeDownload
                        )
                    }
                    uiState.currentTab == MainTab.NOW_PLAYING -> {
                        NowPlayingScreen(
                            viewModel = viewModel,
                            playerState = playerState,
                            hasAudioCapturePermission = visualizerPermissionGranted,
                            onDownloadSong = requestYouTubeDownload
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
                            equalizerState = equalizerState,
                            onRequestScan = requestMusicScan
                        )
                    }
                }
            }
        }
    }

    qualityPickerSong?.let { song ->
        AlertDialog(
            onDismissRequest = { qualityPickerSong = null },
            title = { Text("Chất lượng tải xuống") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tải nhạc về và nghe offline khi không có internet")
                    YouTubeDownloadQuality.entries.forEach { quality ->
                        OutlinedButton(
                            onClick = {
                                qualityPickerSong = null
                                startYouTubeDownload(song, quality)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(quality.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { qualityPickerSong = null }) { Text("Đóng") } }
        )
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
