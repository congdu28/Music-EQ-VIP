package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

    if (song == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
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

    // Pulse animation for active system EQ
    val pulseTransition = rememberInfiniteTransition(label = "eq_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse_alpha"
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
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "primaryColor"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = dynamicPalette.secondary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "secondaryColor"
    )
    val animatedGlow by animateColorAsState(
        targetValue = dynamicPalette.backgroundGlow,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "glowColor"
    )
    val animatedButtonGlow by animateColorAsState(
        targetValue = dynamicPalette.buttonGlow,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
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
                .height(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedGlow.copy(alpha = 0.65f),
                            Color.Transparent
                        ),
                        radius = 750f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Down Button
                Surface(
                    shape = CircleShape,
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.setTab(MainTab.LIBRARY) }
                        .testTag("now_playing_minimize_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Thu nhỏ",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Center Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ĐANG PHÁT",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (song.album.isNotBlank() && song.album != "Unknown Album") song.album else "Offline Player",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right Actions: Edit Tag & Mode Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Edit Metadata Button
                    Surface(
                        shape = CircleShape,
                        color = DarkSurfaceVariant,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.setShowEditMetadata(song) }
                            .testTag("now_playing_edit_tag_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Sửa thông tin bài hát",
                                tint = animatedPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Mode switch: Art vs Full Lyrics Toggle
                    Surface(
                        shape = CircleShape,
                        color = if (displayMode == NowPlayingDisplayMode.FULL_LYRICS) animatedPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant,
                        border = if (displayMode == NowPlayingDisplayMode.FULL_LYRICS) androidx.compose.foundation.BorderStroke(1.dp, animatedPrimary) else null,
                        modifier = Modifier
                            .size(42.dp)
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MAIN CONTENT: Either Album Art Disc or Embedded Karaoke View
            AnimatedContent(
                targetState = displayMode,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "view_mode_transition"
            ) { mode ->
                if (mode == NowPlayingDisplayMode.ALBUM_ART) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Sized for optimal vertical fit (210.dp)
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Blur Glow
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(
                                        elevation = 28.dp,
                                        shape = RoundedCornerShape(38.dp),
                                        ambientColor = ElectricAzure,
                                        spotColor = ElectricAzure
                                    )
                            )

                            // Main Rounded Squircle Surface
                            Surface(
                                shape = RoundedCornerShape(38.dp),
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
                                    // Soundwave Concentric Disc Rings
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .rotate(if (playerState.isPlaying) rotationAngle else 0f)
                                            .clip(CircleShape)
                                            .background(Color(0xFF13171F)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = androidx.compose.foundation.BorderStroke(3.5.dp, animatedPrimary),
                                            modifier = Modifier.size(120.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                // Central Core
                                                Surface(
                                                    shape = CircleShape,
                                                    color = animatedPrimary,
                                                    modifier = Modifier.size(86.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(40.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Top-Right HI-RES Badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = ElectricAzure.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricAzure.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp)
                                            .clickable { viewModel.setShowAudioSpecs(true) }
                                    ) {
                                        Text(
                                            text = if (song.isHiRes) "HI-RES" else song.format,
                                            color = ElectricAzure,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Song Title & Artist
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.4).sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = song.artist,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.toggleFavorite(song) },
                                modifier = Modifier.size(28.dp).testTag("now_playing_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Yêu thích",
                                    tint = if (song.isFavorite) NeonPink else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Compact 3-Line Real-Time Synced Lyrics Mask
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurface.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { displayMode = NowPlayingDisplayMode.FULL_LYRICS }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 4.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (hasLyrics) {
                                        val prevLine = if (activeIndex > 0 && activeIndex - 1 < parsedLyrics.lines.size) {
                                            parsedLyrics.lines[activeIndex - 1].text
                                        } else ""

                                        val currentLine = if (activeIndex >= 0 && activeIndex < parsedLyrics.lines.size) {
                                            parsedLyrics.lines[activeIndex].text
                                        } else song.title

                                        val nextLine = if (activeIndex + 1 < parsedLyrics.lines.size) {
                                            parsedLyrics.lines[activeIndex + 1].text
                                        } else ""

                                        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = prevLine,
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = currentLine,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = (-0.2).sp,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = nextLine,
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                                            Text("Chạm để xem toàn bộ lời bài hát (LRC)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                                            Text("Hỗ trợ đồng bộ Karaoke thời gian thực", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Top & Bottom Gradient Masks
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                        .align(Alignment.TopCenter)
                                        .background(Brush.verticalGradient(listOf(DarkSurface.copy(alpha = 0.8f), Color.Transparent)))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, DarkSurface.copy(alpha = 0.8f))))
                                )
                            }
                        }
                    }
                } else {
                    // Full Embedded Lyrics List View
                    val lyricsListState = rememberLazyListState()
                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0 && activeIndex < parsedLyrics.lines.size) {
                            lyricsListState.animateScrollToItem(maxOf(0, activeIndex - 1))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lời Karaoke đồng bộ",
                                color = ElectricAzure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { viewModel.setShowEditLyrics(true) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElectricAzure)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sửa LRC", fontSize = 11.sp, color = ElectricAzure)
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
                                    Text("Chưa có file lời bài hát LRC", color = TextSecondary, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.setShowEditLyrics(true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricAzure, contentColor = DarkBackground)
                                    ) {
                                        Text("Dán lời LRC ngay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(parsedLyrics.lines) { index, line ->
                                    val isActive = index == activeIndex
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isActive) ElectricAzure.copy(alpha = 0.2f) else Color.Transparent,
                                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, ElectricAzure.copy(alpha = 0.5f)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (line.timeMs >= 0) {
                                                    viewModel.seekTo(line.timeMs)
                                                }
                                            }
                                            .padding(vertical = 6.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = line.text,
                                            color = if (isActive) Color.White else TextSecondary.copy(alpha = 0.5f),
                                            fontSize = if (isActive) 17.sp else 14.sp,
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

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Melody Wave Visualizer (Sóng nhạc theo giai điệu)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                AudioSpectrumVisualizer(
                    isPlaying = playerState.isPlaying,
                    bands = equalizerState.bands,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    customColors = listOf(animatedPrimary, animatedSecondary, NeonPink),
                    style = uiState.visualizerStyle,
                    onToggleStyle = { viewModel.toggleVisualizerStyle() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Immersive Scrubber / Progress Bar
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

            // Monospace Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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

            Spacer(modifier = Modifier.height(10.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        tint = if (playerState.isShuffle) NeonPurple else ElectricAzure
                    )
                }

                // Previous
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(48.dp).testTag("now_playing_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Bài trước",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Immersive Play / Pause Button (Light Pearl with Dark Icon & Dynamic Glow)
                Surface(
                    shape = CircleShape,
                    color = PlayButtonBackground,
                    modifier = Modifier
                        .size(66.dp)
                        .shadow(
                            elevation = 14.dp,
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
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(48.dp).testTag("now_playing_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Bài tiếp theo",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Equalizer Quick Trigger
                IconButton(
                    onClick = { viewModel.setTab(MainTab.EQUALIZER) },
                    modifier = Modifier.size(42.dp).testTag("now_playing_eq_trigger_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Bộ chỉnh EQ",
                        tint = if (equalizerState.isEnabled) ElectricAzure else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback Order Badge (Phát theo thứ tự vs Phát ngẫu nhiên)
                Surface(
                    shape = CircleShape,
                    color = if (playerState.isShuffle) NeonPurple.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (playerState.isShuffle) NeonPurple.copy(alpha = 0.6f) else NeonCyan.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { viewModel.toggleShuffle() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (playerState.isShuffle) Icons.Default.Shuffle else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = if (playerState.isShuffle) NeonPurple else NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (playerState.isShuffle) "PHÁT NGẪU NHIÊN" else "PHÁT THEO THỨ TỰ",
                            color = if (playerState.isShuffle) NeonPurple else NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Equalizer Status Pill Badge
                Surface(
                    shape = CircleShape,
                    color = DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { viewModel.setTab(MainTab.EQUALIZER) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (equalizerState.isEnabled) NeonGreen.copy(alpha = pulseAlpha) else TextMuted
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (equalizerState.isEnabled) "EQ: BẬT" else "EQ: TẮT",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Auxiliary Shortcuts (Repeat, Sleep Timer, Specs, Speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat Mode
                TextButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (playerState.repeatMode != RepeatMode.OFF) ElectricAzure else TextSecondary
                    )
                ) {
                    Icon(
                        imageVector = when (playerState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.OFF -> Icons.Default.Repeat
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        when (playerState.repeatMode) {
                            RepeatMode.ONE -> "1 bài"
                            RepeatMode.ALL -> "Tất cả"
                            RepeatMode.OFF -> "Lặp lại"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Sleep Timer
                TextButton(
                    onClick = { viewModel.setShowSleepTimer(true) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (playerState.isSleepTimerActive) NeonPink else TextSecondary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (playerState.isSleepTimerActive) "${playerState.sleepTimerRemainingSeconds / 60}m" else "Hẹn giờ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Audio Specs Dialog
                TextButton(
                    onClick = { viewModel.setShowAudioSpecs(true) },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thông số", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Edit Song Metadata Dialog
                TextButton(
                    onClick = { viewModel.setShowEditMetadata(song) },
                    colors = ButtonDefaults.textButtonColors(contentColor = animatedPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sửa thẻ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Playback speed (Continuous granular slider & fine-tune)
                var showSpeedDialog by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showSpeedDialog = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (playerState.playbackSpeed != 1.0f) ElectricAzure else TextSecondary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(String.format("%.2fx", playerState.playbackSpeed), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

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

                // Fine-tune increment/decrement buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val newSpeed = maxOf(0.50f, kotlin.math.round((currentSpeed - 0.05f) * 100) / 100f)
                            onSpeedChange(newSpeed)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-0.05x", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val newSpeed = minOf(2.50f, kotlin.math.round((currentSpeed + 0.05f) * 100) / 100f)
                            onSpeedChange(newSpeed)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+0.05x", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Preset Chips
                Text("Mốc chọn nhanh:", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(0.75f, 0.85f, 1.00f, 1.15f, 1.25f, 1.50f, 2.00f)
                    items(presets) { preset ->
                        val isSelected = kotlin.math.abs(currentSpeed - preset) < 0.02f
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSpeedChange(preset) },
                            label = { Text(String.format("%.2fx", preset), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricAzure,
                                selectedLabelColor = DarkBackground,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextPrimary
                            )
                        )
                    }
                }
            }
        },
        containerColor = DarkSurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}
