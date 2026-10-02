package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.player.PlayerUiState
import com.example.player.RepeatMode
import com.example.ui.MainTab
import com.example.ui.MusicViewModel
import com.example.ui.components.AudioSpectrumVisualizer
import com.example.ui.theme.*

enum class NowPlayingDisplayMode {
    ALBUM_ART,
    FULL_LYRICS
}

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    playerState: PlayerUiState,
    modifier: Modifier = Modifier
) {
    // Back navigation returns to library
    BackHandler {
        viewModel.setTab(MainTab.LIBRARY)
    }

    val song = playerState.currentSong
    val uiState by viewModel.appUiState.collectAsState()
    val equalizerState by viewModel.equalizerState.collectAsState()

    var displayMode by remember { mutableStateOf(NowPlayingDisplayMode.ALBUM_ART) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    if (song == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = DarkSurfaceVariant,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Chưa chọn bài hát nào", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Hãy chọn một bài hát từ thư viện để bắt đầu thưởng thức", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.setTab(MainTab.LIBRARY) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAzure, contentColor = DarkBackground),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mở Thư viện nhạc", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Vinyl/Disc rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )

    var isUserDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentPosition = if (isUserDragging) {
        (dragProgress * playerState.totalDurationMs).toLong()
    } else {
        playerState.currentPositionMs
    }

    val progressFraction = if (playerState.totalDurationMs > 0) {
        (currentPosition.toFloat() / playerState.totalDurationMs).coerceIn(0f, 1f)
    } else 0f

    val parsedLyrics = uiState.parsedLyrics
    val activeIndex = uiState.activeLyricIndex
    val hasLyrics = parsedLyrics.lines.isNotEmpty()

    // Dynamic Color Palette extracted from current album art / song
    val dynamicPalette = remember(song.id, song.title) {
        AlbumArtColorExtractor.getPaletteForSong(song)
    }
    val animatedPrimary by animateColorAsState(
        targetValue = dynamicPalette.primary,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "primaryColor"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = dynamicPalette.secondary,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "secondaryColor"
    )
    val animatedGlow by animateColorAsState(
        targetValue = dynamicPalette.backgroundGlow,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "glowColor"
    )
    val animatedButtonGlow by animateColorAsState(
        targetValue = dynamicPalette.buttonGlow,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "buttonGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Atmospheric Top Radial Glow with Dynamic Album Art Tint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedGlow.copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        radius = 600f
                    )
                )
        )

        // Main player layout: uses verticalScroll as fallback for small screens,
        // but carefully dimensioned so standard devices display everything without scrolling.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER BAR: Minimize, Title, Online Lyrics Search, Edit Metadata, Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minimize Button
                Surface(
                    shape = CircleShape,
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.setTab(MainTab.LIBRARY) }
                        .testTag("now_playing_minimize_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Thu nhỏ",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Center Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "ĐANG PHÁT",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = if (song.album.isNotBlank() && song.album != "Unknown Album") song.album else "Hi-Res Audio Player",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Header Action Buttons: Search Lyrics, Edit Tags, Lyrics Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Online Lyrics Search Button with Spinner Indicator
                    Surface(
                        shape = CircleShape,
                        color = if (uiState.isSearchingLyrics) NeonPink.copy(alpha = 0.2f) else DarkSurfaceVariant,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !uiState.isSearchingLyrics) { viewModel.searchLyricsOnline(song) }
                            .testTag("now_playing_search_lyrics_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.isSearchingLyrics) {
                                CircularProgressIndicator(
                                    color = NeonPink,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Tìm lời trên mạng",
                                    tint = if (hasLyrics) TextSecondary else NeonPink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Edit Metadata Button
                    Surface(
                        shape = CircleShape,
                        color = DarkSurfaceVariant,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.setShowEditMetadata(song) }
                            .testTag("now_playing_edit_tag_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Sửa thông tin bài hát",
                                tint = animatedPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Mode switch: Disc vs Full Lyrics
                    Surface(
                        shape = CircleShape,
                        color = if (displayMode == NowPlayingDisplayMode.FULL_LYRICS) animatedPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant,
                        border = if (displayMode == NowPlayingDisplayMode.FULL_LYRICS) androidx.compose.foundation.BorderStroke(1.dp, animatedPrimary) else null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                displayMode = if (displayMode == NowPlayingDisplayMode.ALBUM_ART) {
                                    NowPlayingDisplayMode.FULL_LYRICS
                                } else {
                                    NowPlayingDisplayMode.ALBUM_ART
                                }
                            }
                            .testTag("toggle_lyrics_mode_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (displayMode == NowPlayingDisplayMode.FULL_LYRICS) Icons.Default.Album else Icons.Default.Lyrics,
                                contentDescription = "Chuyển chế độ lời bài hát",
                                tint = if (displayMode == NowPlayingDisplayMode.FULL_LYRICS) animatedPrimary else TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MAIN ARTWORK / LYRICS VIEW: Perfectly proportioned to avoid vertical squishing
            AnimatedContent(
                targetState = displayMode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "view_mode_transition"
            ) { mode ->
                if (mode == NowPlayingDisplayMode.ALBUM_ART) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Proportionate Vinyl Disc Cover (150.dp: fits perfectly on all screens!)
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Blur Glow
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(28.dp),
                                        ambientColor = animatedPrimary,
                                        spotColor = animatedPrimary
                                    )
                            )

                            // Main Rounded Squircle Surface
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(DarkSurfaceVariant, DarkBackground)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Soundwave Concentric Disc Rings (Rotated via graphicsLayer to eliminate recompositions)
                                    Box(
                                        modifier = Modifier
                                            .size(108.dp)
                                            .graphicsLayer {
                                                rotationZ = if (playerState.isPlaying) rotationAngle else 0f
                                            }
                                            .clip(CircleShape)
                                            .background(Color(0xFF13171F)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = androidx.compose.foundation.BorderStroke(2.5.dp, animatedPrimary),
                                            modifier = Modifier.size(100.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                // Central Core
                                                Surface(
                                                    shape = CircleShape,
                                                    color = animatedPrimary,
                                                    modifier = Modifier.size(68.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Top-Right HI-RES Badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = animatedPrimary.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .clickable { viewModel.setShowAudioSpecs(true) }
                                    ) {
                                        Text(
                                            text = if (song.isHiRes) "HI-RES" else song.format,
                                            color = animatedPrimary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Full Embedded Lyrics List View (Fixed height 240dp so controls stay stable)
                    val lyricsListState = rememberLazyListState()
                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0 && activeIndex < parsedLyrics.lines.size) {
                            lyricsListState.animateScrollToItem(maxOf(0, activeIndex - 1))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lời Karaoke đồng bộ",
                                color = animatedPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { viewModel.searchLyricsOnline(song) },
                                    enabled = !uiState.isSearchingLyrics,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonPink)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tìm Online", fontSize = 11.sp, color = NeonPink)
                                }
                                TextButton(
                                    onClick = { viewModel.setShowEditLyrics(true) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = animatedPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sửa LRC", fontSize = 11.sp, color = animatedPrimary)
                                }
                            }
                        }

                        if (!hasLyrics) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Chưa có file lời bài hát", color = TextSecondary, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.searchLyricsOnline(song) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DarkBackground),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tìm lời trên mạng", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(parsedLyrics.lines) { index, line ->
                                    val isActive = index == activeIndex
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isActive) animatedPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.5f)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (line.timeMs >= 0) {
                                                    viewModel.seekTo(line.timeMs)
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = line.text,
                                            color = if (isActive) Color.White else TextSecondary.copy(alpha = 0.5f),
                                            fontSize = if (isActive) 15.sp else 13.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SONG TITLE & ARTIST: Fixed-height single-line text containers (NO line jumping!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp) // Strict fixed container height prevents layout jumping!
            ) {
                Text(
                    text = song.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = (-0.2).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = song.artist,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { viewModel.toggleFavorite(song) },
                        modifier = Modifier.size(24.dp).testTag("now_playing_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Yêu thích",
                            tint = if (song.isFavorite) NeonPink else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // COMPACT SYNCED LYRICS PILL (Strictly fixed 42dp height, NO layout jumping!)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (hasLyrics) {
                            displayMode = NowPlayingDisplayMode.FULL_LYRICS
                        } else {
                            viewModel.searchLyricsOnline(song)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasLyrics) {
                        val activeLineText = if (activeIndex >= 0 && activeIndex < parsedLyrics.lines.size) {
                            parsedLyrics.lines[activeIndex].text
                        } else song.title

                        AnimatedContent(
                            targetState = activeLineText,
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                            label = "lyric_line_crossfade"
                        ) { lineText ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = animatedPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = lineText,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (uiState.isSearchingLyrics) {
                                CircularProgressIndicator(
                                    color = NeonPink,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Đang tự tìm lời trên mạng...",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = animatedPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Chưa có lời • Chạm để tìm online ngay",
                                    color = animatedPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // DYNAMIC MELODY WAVE VISUALIZER (Fixed 38dp height, wave/spectrum toggle)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                AudioSpectrumVisualizer(
                    isPlaying = playerState.isPlaying,
                    bands = equalizerState.bands,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    customColors = listOf(animatedPrimary, animatedSecondary, NeonPink),
                    style = uiState.visualizerStyle,
                    onToggleStyle = { viewModel.toggleVisualizerStyle() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // IMMERSIVE PROGRESS SCRUBBER SLIDER
            Slider(
                value = if (isUserDragging) dragProgress else progressFraction,
                onValueChange = {
                    isUserDragging = true
                    dragProgress = it
                },
                onValueChangeFinished = {
                    val targetMs = (dragProgress * playerState.totalDurationMs).toLong()
                    viewModel.seekTo(targetMs)
                    isUserDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = animatedPrimary,
                    activeTrackColor = animatedPrimary,
                    inactiveTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_seek_slider")
            )

            // STEADY TIME LABELS (Strict Left and Right, NO middle text causing layout shifts!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val safeCurPos = maxOf(0L, currentPosition)
                val safeTotDur = maxOf(0L, playerState.totalDurationMs)
                val curMin = (safeCurPos / 1000) / 60
                val curSec = (safeCurPos / 1000) % 60
                val totMin = (safeTotDur / 1000) / 60
                val totSec = (safeTotDur / 1000) % 60

                Text(
                    text = String.format("%02d:%02d", curMin, curSec),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Start
                )

                // Format & Audio specs badge in middle (Fixed width/height, does NOT push time labels)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.clickable { viewModel.setShowAudioSpecs(true) }
                ) {
                    Text(
                        text = if (song.isHiRes) "Hi-Res ${song.format}" else song.format,
                        color = HiResGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = String.format("%02d:%02d", totMin, totSec),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // PRIMARY PLAYBACK CONTROLS (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle vs Sequential Mode Toggle Button
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(42.dp).testTag("now_playing_shuffle_button")
                ) {
                    Icon(
                        imageVector = if (playerState.isShuffle) Icons.Default.Shuffle else Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = if (playerState.isShuffle) "Đang phát ngẫu nhiên" else "Đang phát theo thứ tự",
                        tint = if (playerState.isShuffle) NeonPurple else animatedPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(46.dp).testTag("now_playing_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Bài trước",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Main Play / Pause Button (Large, Pearl white, Dynamic Glow)
                Surface(
                    shape = CircleShape,
                    color = PlayButtonBackground,
                    modifier = Modifier
                        .size(62.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            spotColor = animatedButtonGlow,
                            ambientColor = Color.Black
                        )
                        .clip(CircleShape)
                        .clickable { viewModel.togglePlayPause() }
                        .testTag("now_playing_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Tạm dừng" else "Phát",
                            tint = PlayButtonContent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Next Button
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(46.dp).testTag("now_playing_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Bài tiếp theo",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Repeat Mode Toggle Button
                IconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier.size(42.dp).testTag("now_playing_repeat_button")
                ) {
                    Icon(
                        imageVector = when (playerState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.OFF -> Icons.Default.Repeat
                        },
                        contentDescription = "Chế độ lặp lại",
                        tint = if (playerState.repeatMode != RepeatMode.OFF) animatedPrimary else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // DEDICATED QUICK CONTROLS BAR: SPEED (PROMINENT!), EQ, SLEEP TIMER, ONLINE LYRICS
            // Fully responsive, auto-ellipsis, single-line protection, never wraps or clips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. TỐC ĐỘ PHÁT (SPEED)
                val isCustomSpeed = playerState.playbackSpeed != 1.0f
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCustomSpeed) animatedPrimary.copy(alpha = 0.22f) else DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCustomSpeed) animatedPrimary else DarkBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showSpeedDialog = true }
                        .testTag("now_playing_speed_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Tốc độ phát",
                            tint = if (isCustomSpeed) animatedPrimary else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.2fx", playerState.playbackSpeed),
                            color = if (isCustomSpeed) animatedPrimary else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. EQUALIZER TRIGGER
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (equalizerState.isEnabled) animatedPrimary.copy(alpha = 0.15f) else DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (equalizerState.isEnabled) animatedPrimary.copy(alpha = 0.6f) else DarkBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setTab(MainTab.EQUALIZER) }
                        .testTag("now_playing_eq_trigger_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Bộ EQ",
                            tint = if (equalizerState.isEnabled) animatedPrimary else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Bộ EQ",
                            color = if (equalizerState.isEnabled) animatedPrimary else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3. SLEEP TIMER
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (playerState.isSleepTimerActive) NeonPink.copy(alpha = 0.2f) else DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (playerState.isSleepTimerActive) NeonPink else DarkBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setShowSleepTimer(true) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Hẹn giờ",
                            tint = if (playerState.isSleepTimerActive) NeonPink else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (playerState.isSleepTimerActive) "${playerState.sleepTimerRemainingSeconds / 60}m" else "Hẹn giờ",
                            color = if (playerState.isSleepTimerActive) NeonPink else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 4. TÌM LỜI ONLINE / CHI TIẾT
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .weight(1.05f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.searchLyricsOnline(song) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Tải lời",
                            tint = NeonPink,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Lời online",
                            color = NeonPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Playback Speed Dialog
            if (showSpeedDialog) {
                CustomPlaybackSpeedDialog(
                    currentSpeed = playerState.playbackSpeed,
                    onSpeedChange = { newSpeed -> viewModel.setPlaybackSpeed(newSpeed) },
                    onDismiss = { showSpeedDialog = false }
                )
            }
        }
    }
}

/**
 * Continuous Granular Playback Speed Dialog:
 * Allows continuous micro-dragging from 0.50x to 2.50x, fine-tune buttons (+/- 0.05x),
 * quick preset chips, and instantaneous real-time speed adaptation.
 */
@Composable
fun CustomPlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricAzure, contentColor = DarkBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Xong", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onSpeedChange(1.00f) },
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Đặt lại 1.00x", fontSize = 12.sp)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = ElectricAzure)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tốc Độ Phát Tùy Chỉnh", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Speed Number Readout
                Text(
                    text = String.format("%.2fx", currentSpeed),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ElectricAzure,
                    letterSpacing = (-0.5).sp
                )

                val diffPercent = kotlin.math.round((currentSpeed - 1.0f) * 100).toInt()
                val statusText = when {
                    diffPercent > 0 -> "+$diffPercent% nhanh hơn"
                    diffPercent < 0 -> "$diffPercent% chậm hơn"
                    else -> "Tốc độ gốc chuẩn (1.00x)"
                }
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = if (diffPercent != 0) NeonPink else TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Continuous Draggable Slider (0.50x to 2.50x)
                Slider(
                    value = currentSpeed,
                    onValueChange = { raw ->
                        val rounded = kotlin.math.round(raw * 100) / 100f
                        onSpeedChange(rounded)
                    },
                    valueRange = 0.50f..2.50f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricAzure,
                        activeTrackColor = ElectricAzure,
                        inactiveTrackColor = DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playback_speed_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.50x", color = TextMuted, fontSize = 10.sp)
                    Text("1.00x (Chuẩn)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("2.50x", color = TextMuted, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fine-tune micro adjustment buttons (+/- 0.05x)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val newS = kotlin.math.max(0.50f, kotlin.math.round((currentSpeed - 0.05f) * 100) / 100f)
                            onSpeedChange(newS)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("-0.05x", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedButton(
                        onClick = {
                            val newS = kotlin.math.min(2.50f, kotlin.math.round((currentSpeed + 0.05f) * 100) / 100f)
                            onSpeedChange(newS)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("+0.05x", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Presets row
                Text("Chọn nhanh tốc độ:", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(6.dp))

                val presets = listOf(0.75f, 0.90f, 1.00f, 1.10f, 1.25f, 1.50f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { presetSpeed ->
                        val isSelected = kotlin.math.abs(currentSpeed - presetSpeed) < 0.02f
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) ElectricAzure else DarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ElectricAzure else DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSpeedChange(presetSpeed) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format("%.2fx", presetSpeed),
                                    color = if (isSelected) DarkBackground else TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
