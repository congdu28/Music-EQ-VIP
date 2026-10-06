package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic

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
import com.example.ui.MusicFolder
import com.example.ui.MusicViewModel
import com.example.ui.theme.*

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    onRequestScan: () -> Unit = { viewModel.scanDeviceAudio() },
    onPickAudioFolder: () -> Unit = {},
    onPickSuggestedFolder: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filteredSongs = viewModel.getFilteredSongs()
    val musicFolders = remember(uiState.songs) { viewModel.getMusicFolders() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .musicScreenBackground()
    ) {
        // Compact Unified Search & Quick Action Header (Saves vertical space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
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
                                fontFamily = GoogleSansFontFamily
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPickAudioFolder,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .testTag("choose_music_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Chọn thư mục quét nhạc",
                        tint = NeonCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Button(
                    onClick = onRequestScan,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(36.dp).testTag("scan_library_button")
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quét", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

        }

        if (!uiState.hasCompletedLibraryScan) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text("Gợi ý thư mục:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                items(listOf("Music", "Download", "Recordings")) { folderName ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPickSuggestedFolder(folderName) }
                            .testTag("suggest_scan_${folderName.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(folderName, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Scanning Status Toast/Banner
        AnimatedVisibility(
            visible = uiState.isScanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NeonCyan.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = NeonCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang quét bài hát trong thư mục...", color = TextPrimary, fontSize = 12.sp)
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
                    LibrarySubTab.ALL_SONGS -> " (${uiState.songs.count { !it.isFavorite }})"
                    LibrarySubTab.FOLDERS -> " (${musicFolders.size})"
                    LibrarySubTab.HI_RES -> " (${uiState.songs.count { it.isHiRes }})"
                    LibrarySubTab.FAVORITES -> " (${uiState.favoriteSongs.size})"
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
                            color = if (isSelected) AccentContent else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (uiState.librarySubTab == LibrarySubTab.ALL_SONGS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shortcuts = listOf(
                    Triple(Icons.Default.Favorite, "Yêu thích", uiState.favoriteSongs.size) to LibrarySubTab.FAVORITES,
                    Triple(Icons.Default.FolderOpen, "Thư mục", musicFolders.size) to LibrarySubTab.FOLDERS,
                    Triple(Icons.Default.HighQuality, "Hi-Res", uiState.songs.count { it.isHiRes }) to LibrarySubTab.HI_RES
                )
                shortcuts.forEach { (shortcut, target) ->
                    val (icon, title, count) = shortcut
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.setLibrarySubTab(target) },
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(icon, contentDescription = null, tint = if (target == LibrarySubTab.FAVORITES) NeonPink else NeonCyan, modifier = Modifier.size(19.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(count.toString(), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area: Folders, Playlists, or Song List
        when (uiState.librarySubTab) {
            LibrarySubTab.FOLDERS -> {

                FolderBrowserView(
                    viewModel = viewModel,
                    uiState = uiState,
                    playerState = playerState,
                    folders = musicFolders,
                    onRequestScan = onRequestScan
                )
            }
            LibrarySubTab.PLAYLISTS -> {
                PlaylistsView(
                    playlists = uiState.playlists,
                    favoriteSongCount = uiState.favoriteSongs.size,
                    onCreatePlaylist = { viewModel.setShowCreatePlaylist(true) },
                    onOpenPlaylist = { playlist -> viewModel.openPlaylist(playlist) },
                    onOpenFavorites = { viewModel.setLibrarySubTab(LibrarySubTab.FAVORITES) }
                )
            }
            else -> {
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
                                    onClick = onRequestScan,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Quét nhạc trong máy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (uiState.librarySubTab) {
                                LibrarySubTab.FAVORITES -> "Bài hát yêu thích"
                                LibrarySubTab.HI_RES -> "Thư viện Hi-Res"
                                else -> "Tất cả bài hát"
                            },
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${filteredSongs.size} bài", color = TextSecondary, fontSize = 11.sp)
                    }

                    // Keep both frequent library actions together with a comfortable touch target.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1.15f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (uiState.librarySubTab == LibrarySubTab.FAVORITES) {
                                        viewModel.playFavoriteQueue(filteredSongs, 0)
                                    } else {
                                        viewModel.playAllSequential(filteredSongs, 0)
                                    }
                                }
                                .testTag("play_sequential_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = NeonCyan.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.65f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Phát tất cả", color = NeonCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(0.9f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setLibrarySubTab(LibrarySubTab.FAVORITES) }
                                .testTag("open_favorites_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = NeonPink.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = NeonPink, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Yêu thích", color = NeonPink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${uiState.favoriteSongs.size}", color = NeonPink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    // Song List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredSongs, key = { it.id }) { song ->
                            val isCurrent = playerState.currentSong?.id == song.id
                            CompactSongItem(
                                song = song,
                                isPlaying = isCurrent && playerState.isPlaying,
                                isCurrentSong = isCurrent,
                                onClick = {
                                    val idx = filteredSongs.indexOf(song)
                                    if (uiState.librarySubTab == LibrarySubTab.FAVORITES) {
                                        viewModel.playFavoriteQueue(filteredSongs, idx)
                                    } else {
                                        viewModel.playQueue(filteredSongs, idx)
                                    }
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(song) },
                                onAddToPlaylist = { viewModel.setShowAddToPlaylist(song) },
                                onEditLyrics = { viewModel.setShowEditLyrics(true) },
                                onEditMetadata = { viewModel.setShowEditMetadata(song) },
                                onViewSpecs = { viewModel.setShowAudioSpecs(true) }
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * Folder Browser View:
 * Allows browsing audio files by their directory / folder hierarchy.
 */
@Composable
fun FolderBrowserView(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    folders: List<MusicFolder>,
    onRequestScan: () -> Unit = { viewModel.scanDeviceAudio() }
) {
    val selectedFolder = uiState.selectedFolder

    if (selectedFolder != null) {
        // Handle Android system back button to exit folder
        BackHandler {
            viewModel.closeFolder()
        }

        // Inside a selected folder
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            // Folder Header with Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.closeFolder() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFolder.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${selectedFolder.songCount} bài hát • ${selectedFolder.path}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Keep only the sequential play action in Library.
            Button(
                onClick = { viewModel.playAllSequential(selectedFolder.songs, 0) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Phát thư mục", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Song list inside this folder
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(selectedFolder.songs, key = { it.id }) { song ->
                    val isCurrent = playerState.currentSong?.id == song.id
                    CompactSongItem(
                        song = song,
                        isPlaying = isCurrent && playerState.isPlaying,
                        isCurrentSong = isCurrent,
                        onClick = {
                            val idx = selectedFolder.songs.indexOf(song)
                            viewModel.playQueue(selectedFolder.songs, idx)
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(song) },
                        onAddToPlaylist = { viewModel.setShowAddToPlaylist(song) },
                        onEditLyrics = { viewModel.setShowEditLyrics(true) },
                        onEditMetadata = { viewModel.setShowEditMetadata(song) },
                        onViewSpecs = { viewModel.setShowAudioSpecs(true) }
                    )
                }
            }
        }
    } else {
        // Folders List View
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.FolderOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Chưa tìm thấy thư mục âm nhạc nào", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestScan,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent)
                    ) {
                        Text("Quét lại bộ nhớ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Thư mục chứa tệp âm thanh trên máy",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(folders) { folder ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.openFolder(folder) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Folder Gradient Icon
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NeonCyan.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Folder Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = folder.path,
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = DarkSurfaceVariant
                                    ) {
                                        Text(
                                            text = "${folder.songCount} bài hát",
                                            color = TextSecondary,
                                            fontSize = 9.5.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (folder.hiResCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = HiResGold.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${folder.hiResCount} Hi-Res",
                                                color = HiResGold,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Play button
                            IconButton(
                                onClick = { viewModel.playAllSequential(folder.songs, 0) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Phát tất cả trong thư mục",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ultra-Compact Song Item (Fits 8+ songs per screen)
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
    onEditMetadata: () -> Unit,
    onViewSpecs: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrentSong) DarkSurfaceElevated else DarkSurface,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isCurrentSong) NeonCyan.copy(alpha = 0.6f) else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("song_item_${song.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sized 38.dp Thumbnail
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isCurrentSong) listOf(NeonCyanDim, NeonCyan) else listOf(CardGradientStart, CardGradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!song.albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Đang phát",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (song.albumArtUri.isNullOrBlank()) {
                    Text(
                        text = song.title.take(1).uppercase(),
                        color = if (isCurrentSong) AccentContent else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }


            Spacer(modifier = Modifier.width(10.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrentSong) NeonCyan else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(1.dp))

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
            IconToggleButton(
                checked = song.isFavorite,
                onCheckedChange = { onFavoriteClick() },
                modifier = Modifier.size(42.dp).testTag("favorite_button_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Bỏ yêu thích" else "Thêm vào yêu thích",
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
                        text = { Text("Chỉnh sửa thông tin bài hát", color = TextPrimary) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showMenu = false
                            onEditMetadata()
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
    onEditMetadata: () -> Unit = {},
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
        onEditMetadata = onEditMetadata,
        onViewSpecs = onViewSpecs
    )
}

@Composable
fun PlaylistsView(
    playlists: List<Playlist>,
    favoriteSongCount: Int,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenFavorites: () -> Unit
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

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = NeonPink.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenFavorites)
                .testTag("favorite_songs_folder")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonPink.copy(alpha = 0.2f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = NeonPink, modifier = Modifier.size(23.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bài hát yêu thích", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("$favoriteSongCount bài hát • chạm để mở", color = TextSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Mở bài hát yêu thích", tint = NeonPink)
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
                    Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Chưa có danh sách phát nào", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onCreatePlaylist,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Tạo danh sách phát đầu tiên", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenPlaylist(playlist) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(playlist.coverGradientStart),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (playlist.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = playlist.description,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}



