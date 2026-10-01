package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Playlist
import com.example.model.Song
import com.example.player.PlayerUiState
import com.example.ui.LibrarySubTab
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.theme.*

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    modifier: Modifier = Modifier
) {
    val filteredSongs = viewModel.getFilteredSongs()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Compact Unified Search & Quick Action Header (Saves vertical space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Streamlined Search Bar
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.searchQuery.isNotEmpty()) NeonCyan else DarkBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (uiState.searchQuery.isNotEmpty()) NeonCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (uiState.searchQuery.isEmpty()) {
                            Text(
                                "Tìm bài hát, ca sĩ, FLAC...",
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = BeVietnamProFontFamily
                            ),
                            cursorBrush = SolidColor(NeonCyan),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_bar_input")
                        )
                    }
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Xóa tìm kiếm", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Quick Shuffle Button
            IconButton(
                onClick = {
                    if (filteredSongs.isNotEmpty()) {
                        viewModel.playQueue(filteredSongs.shuffled(), 0)
                    }
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Trộn bài",
                    tint = NeonPurple,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick Scan Button
            IconButton(
                onClick = { viewModel.scanDeviceAudio() },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Quét máy",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Scanning Status Toast/Banner
        AnimatedVisibility(
            visible = uiState.isScanning || uiState.scanResultMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (uiState.isScanning) NeonCyan.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.isScanning) NeonCyan else NeonGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang quét bài hát trong bộ nhớ máy...", color = TextPrimary, fontSize = 12.sp)
                    } else {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(uiState.scanResultMessage ?: "", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissScanMessage() }, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Subtabs Navigation with embedded count tags (Ultra-compact 30dp height)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(LibrarySubTab.values()) { tab ->
                val isSelected = uiState.librarySubTab == tab
                val countLabel = when (tab) {
                    LibrarySubTab.ALL_SONGS -> " (${uiState.songs.size})"
                    LibrarySubTab.HI_RES -> " (${uiState.songs.count { it.isHiRes }})"
                    LibrarySubTab.FAVORITES -> " (${uiState.songs.count { it.isFavorite }})"
                    LibrarySubTab.PLAYLISTS -> " (${uiState.playlists.size})"
                    LibrarySubTab.ARTISTS -> " (${uiState.songs.map { it.artist }.distinct().size})"
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) NeonCyan else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isSelected) NeonCyan else DarkBorder),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.setLibrarySubTab(tab) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tab.title}$countLabel",
                            color = if (isSelected) DarkBackground else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area: Playlists or Compact Song List
        if (uiState.librarySubTab == LibrarySubTab.PLAYLISTS) {
            PlaylistsView(
                playlists = uiState.playlists,
                onCreatePlaylist = { viewModel.setShowCreatePlaylist(true) },
                onOpenPlaylist = { playlist -> viewModel.openPlaylist(playlist) }
            )
        } else {
            // Song List (Optimized vertical density: fits 8+ songs per screen)
            if (filteredSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicOff,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Không có bài hát nào phù hợp", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (uiState.searchQuery.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.setSearchQuery("") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Xóa tìm kiếm", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.scanDeviceAudio() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Quét nhạc trong máy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredSongs, key = { it.id }) { song ->
                        val isCurrentPlaying = playerState.currentSong?.id == song.id
                        CompactSongItem(
                            song = song,
                            isPlaying = isCurrentPlaying && playerState.isPlaying,
                            isCurrentSong = isCurrentPlaying,
                            onClick = { viewModel.playSong(song) },
                            onFavoriteClick = { viewModel.toggleFavorite(song) },
                            onAddToPlaylist = { viewModel.setShowAddToPlaylist(song) },
                            onEditLyrics = {
                                viewModel.playSong(song)
                                viewModel.setShowEditLyrics(true)
                            },
                            onViewSpecs = {
                                viewModel.playSong(song)
                                viewModel.setShowAudioSpecs(true)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact, modern Song Item designed to maximize screen real estate on mobile devices.
 * Slim profile (~50dp height) allows 8+ songs to fit comfortably on screen.
 */
@Composable
fun CompactSongItem(
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditLyrics: () -> Unit,
    onViewSpecs: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrentSong) DarkSurfaceElevated else DarkSurface,
        border = if (isCurrentSong) androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)) else androidx.compose.foundation.BorderStroke(0.6.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("song_item_${song.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Album Artwork (38dp x 38dp)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isCurrentSong) listOf(NeonCyan, NeonViolet) else listOf(DarkSurfaceVariant, DarkBorder)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Đang phát",
                        tint = if (isCurrentSong) DarkBackground else NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrentSong) DarkBackground else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Metadata Column
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Song title + Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.title,
                        color = if (isCurrentSong) NeonCyan else TextPrimary,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val minutes = (song.durationMs / 1000) / 60
                    val seconds = (song.durationMs / 1000) % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Line 2: Artist + Format Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.artist,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Compact Format Pill
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = if (song.isHiRes) HiResGold.copy(alpha = 0.15f) else DarkSurfaceVariant,
                        border = if (song.isHiRes) androidx.compose.foundation.BorderStroke(0.6.dp, HiResGold) else null
                    ) {
                        Text(
                            text = if (song.isHiRes) "${song.format} ${song.bitDepth}b" else song.format,
                            color = if (song.isHiRes) HiResGold else TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Action Buttons
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Yêu thích",
                    tint = if (song.isFavorite) NeonPink else TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("song_menu_button_${song.id}")
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
                        text = { Text("Chỉnh sửa lời bài hát (LRC)", color = TextPrimary) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Lyrics, contentDescription = null, tint = NeonPurple) },
                        onClick = {
                            showMenu = false
                            onEditLyrics()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Thông số Hi-Res Audio", color = TextPrimary) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = HiResGold) },
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

@Composable
fun SongCardItem(
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditLyrics: () -> Unit,
    onViewSpecs: () -> Unit
) {
    CompactSongItem(
        song = song,
        isPlaying = isPlaying,
        isCurrentSong = isCurrentSong,
        onClick = onClick,
        onFavoriteClick = onFavoriteClick,
        onAddToPlaylist = onAddToPlaylist,
        onEditLyrics = onEditLyrics,
        onViewSpecs = onViewSpecs
    )
}

@Composable
fun PlaylistsView(
    playlists: List<Playlist>,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Danh Sách Phát Của Bạn",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Button(
                onClick = onCreatePlaylist,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = NeonCyan),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tạo mới", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Chưa có danh sách phát nào", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onCreatePlaylist,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Tạo danh sách phát đầu tiên", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenPlaylist(playlist) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(playlist.coverGradientStart), Color(playlist.coverGradientEnd))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = playlist.description.ifEmpty { "Danh sách nhạc cá nhân" },
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}
