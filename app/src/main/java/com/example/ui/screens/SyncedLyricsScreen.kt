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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
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
    // Back gesture returns to the player, where lyrics can be opened again from the quick tools.
    BackHandler {
        viewModel.setTab(MainTab.NOW_PLAYING)
    }

    val song = playerState.currentSong
    val parsedLyrics = uiState.parsedLyrics
    val activeIndex = uiState.activeLyricIndex
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Follow the active line continuously, matching the embedded lyrics view in Now Playing.
    LaunchedEffect(activeIndex, song?.id, parsedLyrics.lines.size) {
        if (activeIndex >= 0 && activeIndex < parsedLyrics.lines.size) {
            listState.animateScrollToItem(maxOf(0, activeIndex - 1))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .musicScreenBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.setTab(MainTab.NOW_PLAYING) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại trình phát", tint = TextPrimary)
                }
                Text(
                    "Lời bài hát",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { song?.let { viewModel.searchLyricsOnline(it) } },
                    enabled = song != null && !uiState.isSearchingLyrics
                ) {
                    if (uiState.isSearchingLyrics) {
                        CircularProgressIndicator(color = NeonViolet, strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Tìm lời chính xác trên LRCLIB", tint = NeonPink)
                    }
                }
                IconButton(onClick = { viewModel.setTab(MainTab.SETTINGS) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = TextSecondary)
                }
            }

            // Responsive title and actions: controls move to their own row on every width.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonPink.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink)
                        ) {
                            Text(
                                text = if (parsedLyrics.lines.any { it.timeMs >= 0 }) "ĐỒNG BỘ LRC" else "LỜI VĂN BẢN",
                                color = if (parsedLyrics.lines.any { it.timeMs >= 0 }) NeonPink else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (parsedLyrics.sourceName == "Gemini AI") {
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NeonViolet.copy(alpha = 0.22f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonViolet.copy(alpha = 0.8f))
                            ) {
                                Text(
                                    text = "AI",
                                    color = NeonViolet,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = song?.title ?: "Chưa chọn bài hát",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (song != null && parsedLyrics.lines.any { it.timeMs >= 0 }) "${song.artist} • Lời chạy theo mốc thời gian" else if (song != null) "${song.artist} • Chưa có mốc thời gian đồng bộ" else "Chọn bài hát để phát",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (parsedLyrics.sourceName == "Gemini AI") {
                        Text(
                            text = if (parsedLyrics.timingSource != null) "Lời do Gemini AI tạo; mốc thời gian AI ước tính — vui lòng đối chiếu." else "Lời do Gemini AI tạo — vui lòng đối chiếu trước khi sử dụng.",
                            color = NeonViolet,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (parsedLyrics.sourceUrls.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Nguồn:", color = TextSecondary, fontSize = 10.sp)
                                parsedLyrics.sourceUrls.take(3).forEachIndexed { index, url ->
                                    Text(
                                        text = "${index + 1}",
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Throwable) { }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                var showSearchOptions by remember { mutableStateOf(false) }

                // Actions: Search Online & Edit / Paste Lyrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (song != null) {
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { showSearchOptions = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = NeonPink),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !uiState.isSearchingLyrics,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isSearchingLyrics) {
                                    CircularProgressIndicator(color = NeonPink, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                } else {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tìm Lời", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            DropdownMenu(
                                expanded = showSearchOptions,
                                onDismissRequest = { showSearchOptions = false },
                                modifier = Modifier.background(DarkSurfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = NeonPink, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Tìm lời chính xác · LRCLIB", color = TextPrimary, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        showSearchOptions = false
                                        viewModel.searchLyricsOnline(song)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Tìm/tạo lời bằng Gemini AI", color = TextPrimary, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        showSearchOptions = false
                                        viewModel.searchLyricsOnline(song, useGemini = true)
                                    }
                                )
                                if (!song.lyrics.isNullOrBlank()) DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("AI ước tính mốc từ lời hiện có", color = TextPrimary, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        showSearchOptions = false
                                        viewModel.alignLyricsWithGemini(song.lyrics.orEmpty())
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Tìm kiếm trên Google", color = TextPrimary, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        showSearchOptions = false
                                        try {
                                            val query = "lời bài hát ${song.title} ${song.artist} lrc"
                                            val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // ignore
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.setShowEditLyrics(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = NeonCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("edit_lyrics_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sửa lời", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = TextHighlight, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Độ lệch: ${song?.lrcOffsetMs ?: 0}ms",
                            color = TextHighlight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Chỉnh thời gian lời", color = TextSecondary, fontSize = 10.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OffsetButton(label = "-500ms", modifier = Modifier.weight(1f)) { viewModel.adjustLyricsOffset(-500) }
                        OffsetButton(label = "-100ms", modifier = Modifier.weight(1f)) { viewModel.adjustLyricsOffset(-100) }
                        OffsetButton(label = "+100ms", modifier = Modifier.weight(1f)) { viewModel.adjustLyricsOffset(100) }
                        OffsetButton(label = "+500ms", modifier = Modifier.weight(1f)) { viewModel.adjustLyricsOffset(500) }
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (song != null) {
                                Button(
                                    onClick = { viewModel.searchLyricsOnline(song) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = AccentContent),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !uiState.isSearchingLyrics,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (uiState.isSearchingLyrics) {
                                        CircularProgressIndicator(color = AccentContent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tìm lời online", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                                if (uiState.geminiLyricsSuggestionSongId == song.id && !uiState.isSearchingLyrics) {
                                    Text("Không có lời khớp. Gemini có thể tạo lời tham khảo và có thể sai.", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    OutlinedButton(
                                        onClick = { viewModel.searchLyricsOnline(song, useGemini = true) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Thử Gemini AI", fontSize = 12.sp)
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.setShowEditLyrics(true) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dán file LRC", fontSize = 12.5.sp)
                            }
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
                                isActive -> TextPrimary
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

    }
}

@Composable
private fun OffsetButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkBorder),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)
        )
    }
}
