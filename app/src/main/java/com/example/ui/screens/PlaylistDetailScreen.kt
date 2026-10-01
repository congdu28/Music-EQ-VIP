package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Playlist
import com.example.model.Song
import com.example.player.PlayerUiState
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.theme.*

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    viewModel: MusicViewModel,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hardware/System back button support
    BackHandler {
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header Banner with Playlist Gradient & Status Bar Insets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(playlist.coverGradientStart),
                            Color(playlist.coverGradientEnd).copy(alpha = 0.6f),
                            DarkBackground
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                if (playlist.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlist.description,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${songs.size} bài hát",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Action Buttons: Play all, Shuffle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (songs.isNotEmpty()) {
                        viewModel.playQueue(songs, 0)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("playlist_play_all_button")
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Phát tất cả", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    if (songs.isNotEmpty()) {
                        viewModel.playQueue(songs.shuffled(), 0)
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("playlist_shuffle_button")
            ) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trộn bài", fontWeight = FontWeight.Bold)
            }
        }

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Danh sách phát này chưa có bài hát nào", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isCurrent = playerState.currentSong?.id == song.id
                    SongCardItem(
                        song = song,
                        isPlaying = isCurrent && playerState.isPlaying,
                        isCurrentSong = isCurrent,
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
