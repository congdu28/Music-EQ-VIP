package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.YouTubeMusicService
import com.example.model.Song
import com.example.player.PlayerUiState
import com.example.ui.MainTab
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

private val YouTubeRed = Color(0xFFFF0033)
private val YouTubeRedLight = Color(0xFFFF4D6D)

@Composable
fun YouTubeOnlineScreen(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    modifier: Modifier = Modifier
) {
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val resultsListState = rememberLazyListState()

    LaunchedEffect(
        resultsListState,
        uiState.youtubeSongs.size,
        uiState.youtubeContinuation,
        uiState.isSearchingYouTube,
        uiState.isLoadingMoreYouTube
    ) {
        if (uiState.isSearchingYouTube || uiState.isLoadingMoreYouTube ||
            uiState.youtubeContinuation == null || uiState.youtubeSongs.isEmpty()
        ) return@LaunchedEffect

        snapshotFlow {
            val layout = resultsListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 6
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) viewModel.loadMoreYouTube()
        }
    }

    // Pressing system back returns to Library
    BackHandler {
        viewModel.setTab(MainTab.LIBRARY)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .musicScreenBackground()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // DEDICATED SEARCH BAR
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (uiState.youtubeQuery.isNotEmpty()) YouTubeRed else DarkBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (uiState.youtubeQuery.isNotEmpty()) YouTubeRed else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (uiState.youtubeQuery.isEmpty()) {
                        Text(
                            "Tìm bài hát, ca sĩ, remix trên YouTube...",
                            color = TextMuted,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = uiState.youtubeQuery,
                        onValueChange = { viewModel.setYouTubeQuery(it) },
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = GoogleSansFontFamily,
                            fontWeight = FontWeight.Normal
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(YouTubeRed),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.searchYouTube(uiState.youtubeQuery)
                            isSearchFocused = false
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isSearchFocused = it.isFocused }
                            .testTag("youtube_search_input")
                    )
                }

                if (uiState.youtubeQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.setYouTubeQuery("") },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Xóa",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        viewModel.searchYouTube(uiState.youtubeQuery)
                        isSearchFocused = false
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = YouTubeRed, modifier = Modifier.size(22.dp))
                }
            }
        }

        if (isSearchFocused && uiState.youtubeSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 216.dp)) {
                    items(uiState.youtubeSuggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isSearchFocused = false
                                    focusManager.clearFocus()
                                    viewModel.searchYouTube(suggestion)
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = suggestion,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // POPULAR CURATED CATEGORIES ROW
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(YouTubeMusicService.CATEGORIES) { cat ->
                val isCatSelected = uiState.selectedYouTubeCategory == cat && uiState.youtubeQuery.isEmpty()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isCatSelected) YouTubeRed else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isCatSelected) YouTubeRed else DarkBorder
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { viewModel.selectYouTubeCategory(cat) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            color = if (isCatSelected) Color.White else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // CONTENT AREA: Takes all remaining screen space (weight 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                uiState.isSearchingYouTube -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = YouTubeRed,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Đang tải bài hát chất lượng cao từ YouTube...",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Error State
                uiState.youtubeErrorMessage != null && uiState.youtubeSongs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = uiState.youtubeErrorMessage,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.selectYouTubeCategory(uiState.selectedYouTubeCategory) },
                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Thử lại", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Empty State
                uiState.youtubeSongs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Chọn một danh mục ở trên hoặc nhập từ khóa để tìm kiếm nhạc YouTube",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Songs List
                else -> {
                    LazyColumn(
                        state = resultsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.youtubeSongs,
                            key = { index, song -> "${song.filePath}_$index" }
                        ) { index, song ->
                            val videoId = YouTubeMusicService.videoIdFor(song).orEmpty()
                            val current = playerState.currentSong
                            val isPlayingOnline = current != null && (
                                current.album == "YouTube Online" ||
                                current.format.contains("YouTube") ||
                                current.filePath.startsWith("yt://")
                            )
                            val isCurrent = isPlayingOnline && (
                                current?.id == song.id ||
                                (current?.filePath?.removePrefix("yt://") == song.filePath.removePrefix("yt://"))
                            )
                            YouTubeSongItem(
                                song = song,
                                isPlaying = isCurrent && playerState.isPlaying,
                                isLoadingStream = isCurrent && playerState.isLoadingOnlineStream,
                                isCurrentSong = isCurrent,
                                isDownloading = videoId in uiState.youtubeDownloadProgress,
                                downloadProgress = uiState.youtubeDownloadProgress[videoId] ?: 0,
                                isDownloaded = videoId in uiState.downloadedYouTubeVideoIds,
                                onClick = {
                                    if (isCurrent && playerState.isPlaying) {
                                        viewModel.pausePlayback()
                                    } else if (isCurrent && !playerState.isPlaying && !playerState.isLoadingOnlineStream) {
                                        viewModel.resumePlayback()
                                    } else {
                                        viewModel.playQueue(uiState.youtubeSongs, index)
                                    }
                                },
                                onDownload = { viewModel.toggleYouTubeSongDownload(song) },
                                onAddToPlaylist = { viewModel.setShowAddToPlaylist(song) },
                                onViewSpecs = { viewModel.setShowAudioSpecs(true) }
                            )
                        }
                        item(key = "youtube_results_footer") {
                            when {
                                uiState.isLoadingMoreYouTube -> Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(color = YouTubeRed, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Đang tải thêm bài hát…", color = TextSecondary, fontSize = 12.sp)
                                }
                                uiState.youtubePaginationError != null -> TextButton(
                                    onClick = viewModel::retryLoadMoreYouTube,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Text(uiState.youtubePaginationError, color = YouTubeRed, fontSize = 12.sp)
                                }
                                uiState.youtubeContinuation == null -> Text(
                                    "Đã tải hết kết quả hiện có",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeSongItem(
    song: Song,
    isPlaying: Boolean,
    isLoadingStream: Boolean,
    isCurrentSong: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onViewSpecs: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val minutes = (song.durationMs / 1000) / 60
    val seconds = (song.durationMs / 1000) % 60

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentSong) YouTubeRed.copy(alpha = 0.08f) else Color.Transparent,
        border = if (isCurrentSong) androidx.compose.foundation.BorderStroke(
            1.dp,
            YouTubeRed.copy(alpha = 0.35f)
        ) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Landscape artwork matches YouTube's familiar thumbnail layout.
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!song.albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                ) {
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                if (isLoadingStream) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = YouTubeRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Đang phát",
                            tint = YouTubeRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = song.title,
                    color = if (isCurrentSong) YouTubeRedLight else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Phát trực tuyến trên YouTube Music",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDownload,
                    enabled = !isDownloaded || isDownloading,
                    modifier = Modifier.size(36.dp).testTag("youtube_download_${song.id}")
                ) {
                    when {
                        isDownloading -> Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { downloadProgress.coerceIn(0, 100) / 100f },
                                color = YouTubeRed,
                                trackColor = DarkBorder,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Icon(Icons.Default.Close, contentDescription = "Hủy tải", tint = YouTubeRed, modifier = Modifier.size(12.dp))
                        }
                        isDownloaded -> Icon(Icons.Default.CheckCircle, contentDescription = "Đã tải", tint = NeonCyan, modifier = Modifier.size(20.dp))
                        else -> Icon(Icons.Default.CloudDownload, contentDescription = "Tải nhạc về thiết bị", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Tùy chọn",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkSurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                when {
                                    isDownloaded -> "Đã tải vào thư viện offline"
                                    isDownloading -> "Đang tải ${downloadProgress.coerceIn(0, 100)}%"
                                    else -> "Tải nhạc về thiết bị"
                                },
                                color = TextPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                        },
                        enabled = !isDownloaded && !isDownloading,
                        onClick = {
                            showMenu = false
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Thêm vào danh sách phát", color = TextPrimary) },
                        leadingIcon = { Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Thông số âm thanh (EQ/Bass Boost)", color = TextPrimary) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Equalizer, contentDescription = null, tint = HiResGold) },
                        onClick = {
                            showMenu = false
                            onViewSpecs()
                        }
                    )
                }
            }
        }
    }
}
