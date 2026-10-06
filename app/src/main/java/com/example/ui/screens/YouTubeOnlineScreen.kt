package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            shape = RoundedCornerShape(14.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (uiState.youtubeQuery.isNotEmpty()) YouTubeRed else DarkBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
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
                            fontSize = 12.5.sp,
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

                Button(
                    onClick = {
                        viewModel.searchYouTube(uiState.youtubeQuery)
                        isSearchFocused = false
                        focusManager.clearFocus()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Tìm", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(YouTubeMusicService.CATEGORIES) { cat ->
                val isCatSelected = uiState.selectedYouTubeCategory == cat && uiState.youtubeQuery.isEmpty()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCatSelected) YouTubeRed else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isCatSelected) YouTubeRed else DarkBorder
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.selectYouTubeCategory(cat) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            color = if (isCatSelected) Color.White else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // QUICK ACTIONS: PLAY ALL & SHUFFLE (Visible when songs loaded)
        if (uiState.youtubeSongs.isNotEmpty() && !uiState.isSearchingYouTube) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = YouTubeRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.playQueue(uiState.youtubeSongs, 0)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Phát tất cả (${uiState.youtubeSongs.size})",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonPurple.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.playAllShuffled(uiState.youtubeSongs)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Trộn bài ngẫu nhiên",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // CONTENT AREA: Takes all remaining screen space (weight 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                // Loading State
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.youtubeSongs,
                            key = { index, song -> "${song.filePath}_$index" }
                        ) { index, song ->
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
                                onClick = {
                                    if (isCurrent && playerState.isPlaying) {
                                        viewModel.pausePlayback()
                                    } else if (isCurrent && !playerState.isPlaying && !playerState.isLoadingOnlineStream) {
                                        viewModel.resumePlayback()
                                    } else {
                                        viewModel.playQueue(uiState.youtubeSongs, index)
                                    }
                                },
                                onAddToPlaylist = { viewModel.setShowAddToPlaylist(song) },
                                onViewSpecs = { viewModel.setShowAudioSpecs(true) }
                            )
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
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onViewSpecs: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrentSong) DarkSurfaceElevated else DarkSurface,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isCurrentSong) YouTubeRed.copy(alpha = 0.7f) else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with badge and play state
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                        modifier = Modifier.size(20.dp)
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

            // Title & Channel
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrentSong) YouTubeRedLight else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // YouTube Red Tag
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = YouTubeRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(0.6.dp, YouTubeRed)
                    ) {
                        Text(
                            text = "YouTube",
                            color = YouTubeRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Duration Tag
                    val minutes = (song.durationMs / 1000) / 60
                    val seconds = (song.durationMs / 1000) % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Play button shortcut
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                    tint = if (isCurrentSong) YouTubeRed else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // More Options
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Tùy chọn",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkSurfaceElevated)
                ) {
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
