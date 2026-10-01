package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyrics.LyricLine
import com.example.player.PlayerUiState
import com.example.ui.MainTab
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.theme.*

@Composable
fun SyncedLyricsScreen(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    modifier: Modifier = Modifier
) {
    // Back gesture returns to Library
    BackHandler {
        viewModel.setTab(MainTab.LIBRARY)
    }

    val song = playerState.currentSong
    val parsedLyrics = uiState.parsedLyrics
    val activeIndex = uiState.activeLyricIndex
    val listState = rememberLazyListState()

    var isUserScrolledAway by remember { mutableStateOf(false) }

    // Auto-scroll to active lyric smoothly without sudden snapping
    LaunchedEffect(activeIndex) {
        if (!isUserScrolledAway && activeIndex >= 0 && activeIndex < parsedLyrics.lines.size) {
            val targetScrollIndex = maxOf(0, activeIndex - 2)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    // Detect when user starts scrolling manually
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolledAway = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonPink.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink)
                        ) {
                            Text(
                                text = "KARAOKE LRC",
                                color = NeonPink,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = song?.title ?: "Chưa chọn bài hát",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (song != null) "${song.artist} • Lời đồng bộ thời gian thực" else "Chọn bài hát để phát",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Edit / Paste Lyrics button
                Button(
                    onClick = { viewModel.setShowEditLyrics(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = NeonCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("edit_lyrics_button")
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sửa LRC", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Timing Offset Tuner Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = TextHighlight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Độ lệch: ${song?.lrcOffsetMs ?: 0}ms",
                            color = TextHighlight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OffsetButton(label = "-500ms") { viewModel.adjustLyricsOffset(-500) }
                        OffsetButton(label = "-100ms") { viewModel.adjustLyricsOffset(-100) }
                        OffsetButton(label = "+100ms") { viewModel.adjustLyricsOffset(100) }
                        OffsetButton(label = "+500ms") { viewModel.adjustLyricsOffset(500) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Lyrics List
            if (parsedLyrics.lines.isEmpty()) {
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
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Chưa có lời bài hát cho ca khúc này", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Dán file định dạng [00:12.34] để hát karaoke đồng bộ", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.setShowEditLyrics(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thêm lời bài hát (LRC)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(parsedLyrics.lines, key = { index, _ -> index }) { index, line ->
                        val isActive = index == activeIndex
                        val isPast = index < activeIndex

                        val textColor by animateColorAsState(
                            targetValue = when {
                                isActive -> Color.White
                                isPast -> TextPrimary.copy(alpha = 0.55f)
                                else -> TextSecondary.copy(alpha = 0.35f)
                            },
                            animationSpec = tween(150),
                            label = "lyric_color"
                        )

                        // Constant container padding to prevent layout bouncing
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) ElectricAzure.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, ElectricAzure) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (line.timeMs >= 0) {
                                        viewModel.seekTo(line.timeMs)
                                        isUserScrolledAway = false
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("lyric_line_$index")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Consistent font size (18.sp) and line height (26.sp)
                                // to eliminate text jumping / flickering
                                Text(
                                    text = line.text,
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 26.sp
                                )

                                if (line.timeMs >= 0 && isActive) {
                                    val totalSec = line.timeMs / 1000
                                    val min = totalSec / 60
                                    val sec = totalSec % 60
                                    Text(
                                        text = String.format("▶ %02d:%02d • Chạm để phát lại", min, sec),
                                        color = ElectricAzure,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Re-Center Button when user scrolled away
        if (isUserScrolledAway && activeIndex >= 0) {
            Surface(
                shape = CircleShape,
                color = ElectricAzure,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .clickable {
                        isUserScrolledAway = false
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cuộn đến câu đang hát",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OffsetButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
