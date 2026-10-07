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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.example.ui.PlayerAmbientMode
import com.example.ui.components.AudioSpectrumVisualizer
import com.example.ui.theme.*
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.withTimeoutOrNull

enum class NowPlayingDisplayMode {
    ALBUM_ART,
    FULL_LYRICS
}

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    playerState: PlayerUiState,
    hasAudioCapturePermission: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Back navigation returns to library
    BackHandler {
        viewModel.setTab(MainTab.LIBRARY)
    }

    val uiState by viewModel.appUiState.collectAsState()
    val pendingOnlineSong = uiState.pendingOnlineSong
    val song = pendingOnlineSong ?: playerState.currentSong
    val equalizerState by viewModel.equalizerState.collectAsState()

    // Only a favorite selection opts into the dedicated blocking loading screen.
    // YouTube playback from search and other screens keeps the regular player UI.
    val isLoadingSelectedOnlineSong = pendingOnlineSong != null

    if (isLoadingSelectedOnlineSong && song != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .musicScreenBackground()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                ) {
                    if (!song.albumArtUri.isNullOrBlank()) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = "Ảnh bìa ${song.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                CircularProgressIndicator(color = ElectricAzure, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Đang tải bài hát",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = song.title,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Đang chuẩn bị dữ liệu phát từ YouTube…",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var displayMode by remember { mutableStateOf(NowPlayingDisplayMode.ALBUM_ART) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current

    LaunchedEffect(song?.filePath) {
        if (song != null) displayMode = NowPlayingDisplayMode.ALBUM_ART
    }

    if (song == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .musicScreenBackground(),
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
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAzure, contentColor = AccentContent),
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
    val hasSyncedLyrics = parsedLyrics.lines.any { it.timeMs >= 0 }
    val screenHeight = configuration.screenHeightDp.dp
    val compactWidth = configuration.screenWidthDp < 360
    val lyricsPanelHeight = (screenHeight * 0.45f).coerceIn(250.dp, 430.dp)
    val artworkSize = (screenHeight * 0.27f).coerceIn(165.dp, 220.dp)
    val isLyricsMode = displayMode == NowPlayingDisplayMode.FULL_LYRICS

    // Use the accent selected in Settings throughout the player.
    val selectedAccent = Color(uiState.accentColor)
    val secondaryAccent = lerp(selectedAccent, if (uiState.isDarkTheme) Color.White else Color.Black, 0.18f)
    val animatedPrimary by animateColorAsState(
        targetValue = selectedAccent,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "primaryColor"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = secondaryAccent,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "secondaryColor"
    )
    val animatedButtonGlow by animateColorAsState(
        targetValue = selectedAccent,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "buttonGlow"
    )
    val context = LocalContext.current
    val albumAmbientColor by produceState<Color?>(
        initialValue = null,
        key1 = song.albumArtUri,
        key2 = uiState.playerAmbientMode
    ) {
        value = null
        if (uiState.playerAmbientMode == PlayerAmbientMode.ALBUM && !song.albumArtUri.isNullOrBlank()) {
            value = try {
                withTimeoutOrNull(5_000L) {
                    val request = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .size(96, 96)
                        .allowHardware(false)
                        .build()
                    val result = Coil.imageLoader(context).execute(request) as? SuccessResult
                    val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
                    bitmap?.let { AlbumArtColorExtractor.extractFromBitmap(it).primary }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    val rgbHue = if (uiState.playerAmbientMode == PlayerAmbientMode.RGB || uiState.playerAmbientMode == PlayerAmbientMode.AURORA) {
        val hue by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(if (uiState.playerAmbientMode == PlayerAmbientMode.AURORA) 16_000 else 24_000, easing = LinearEasing)
            ),
            label = "ambient_rgb_hue"
        )
        hue
    } else 0f
    val ambientColor = when (uiState.playerAmbientMode) {
        PlayerAmbientMode.OFF -> Color.Transparent
        PlayerAmbientMode.ALBUM -> albumAmbientColor ?: selectedAccent
        PlayerAmbientMode.RGB -> Color(android.graphics.Color.HSVToColor(floatArrayOf(rgbHue, 0.70f, 0.90f)))
        PlayerAmbientMode.AURORA -> Color(android.graphics.Color.HSVToColor(floatArrayOf(rgbHue, 0.84f, 0.96f)))
        PlayerAmbientMode.ACCENT -> selectedAccent
    }
    val ambientSecondary = when (uiState.playerAmbientMode) {
        PlayerAmbientMode.RGB -> Color(android.graphics.Color.HSVToColor(floatArrayOf((rgbHue + 110f) % 360f, 0.70f, 0.90f)))
        PlayerAmbientMode.AURORA -> Color(android.graphics.Color.HSVToColor(floatArrayOf((rgbHue + 115f) % 360f, 0.82f, 0.94f)))
        PlayerAmbientMode.ACCENT -> secondaryAccent
        else -> lerp(ambientColor, selectedAccent, 0.35f)
    }
    val ambientTertiary = when (uiState.playerAmbientMode) {
        PlayerAmbientMode.AURORA -> Color(android.graphics.Color.HSVToColor(floatArrayOf((rgbHue + 235f) % 360f, 0.82f, 0.95f)))
        PlayerAmbientMode.RGB -> Color(android.graphics.Color.HSVToColor(floatArrayOf((rgbHue + 225f) % 360f, 0.70f, 0.90f)))
        else -> lerp(ambientColor, ambientSecondary, 0.55f)
    }
    val animatedAmbient by animateColorAsState(
        targetValue = ambientColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "ambientColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .musicScreenBackground()
    ) {
        if (uiState.playerAmbientMode != PlayerAmbientMode.OFF) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val baseAlpha = if (uiState.isDarkTheme) 0.25f else 0.14f
                        val glowAlpha = if (uiState.isDarkTheme) 0.42f else 0.25f
                        val radius = size.maxDimension * 0.92f
                        drawRect(
                            Brush.linearGradient(
                                listOf(
                                    animatedAmbient.copy(alpha = baseAlpha),
                                    ambientSecondary.copy(alpha = baseAlpha * 0.72f),
                                    ambientTertiary.copy(alpha = baseAlpha * 0.82f)
                                )
                            )
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(animatedAmbient.copy(alpha = glowAlpha), Color.Transparent),
                                center = Offset(size.width * 0.48f, size.height * 0.27f),
                                radius = radius
                            )
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(ambientSecondary.copy(alpha = glowAlpha * 0.86f), Color.Transparent),
                                center = Offset(size.width * 0.82f, size.height * 0.78f),
                                radius = radius * 0.72f
                            )
                        )
                    }
            )
        }

        // Main player layout: uses verticalScroll as fallback for small screens,
        // but carefully dimensioned so standard devices display everything without scrolling.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .animateContentSize(animationSpec = tween(240, easing = FastOutSlowInEasing))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER BAR: Minimize, Title, Online Lyrics Search, Edit Metadata
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minimize Button
                Surface(
                    shape = CircleShape,
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .size(44.dp)
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

                // Header Action Buttons: Search Lyrics and Edit Tags
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Online Lyrics Search Button with Spinner Indicator
                    Surface(
                        shape = CircleShape,
                        color = if (uiState.isSearchingLyrics) NeonPink.copy(alpha = 0.2f) else DarkSurfaceVariant,
                        modifier = Modifier
                            .size(44.dp)
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
                            .size(44.dp)
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

                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            NowPlayingModeSelector(
                mode = displayMode,
                accent = animatedPrimary,
                onModeChange = { displayMode = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // MAIN ARTWORK / LYRICS VIEW: Perfectly proportioned to avoid vertical squishing
            AnimatedContent(
                targetState = displayMode,
                transitionSpec = {
                    val direction = if (targetState == NowPlayingDisplayMode.FULL_LYRICS) 1 else -1
                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { direction * it / 14 } +
                        scaleIn(initialScale = 0.985f, animationSpec = tween(280, easing = FastOutSlowInEasing))) togetherWith
                        (fadeOut(tween(160, easing = FastOutSlowInEasing)) +
                            slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -direction * it / 14 } +
                            scaleOut(targetScale = 0.985f, animationSpec = tween(220, easing = FastOutSlowInEasing)))
                },
                label = "view_mode_transition"
            ) { mode ->
                if (mode == NowPlayingDisplayMode.ALBUM_ART) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Adaptive cover size gives artwork the visual priority shown in the reference.
                        Box(
                            modifier = Modifier
                                .size(artworkSize)
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
                                    if (song.albumArtUri != null) {
                                        AsyncImage(
                                            model = song.albumArtUri,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                                        )
                                        // Subtle top-bottom gradient overlay for badge readability
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Black.copy(alpha = 0.35f),
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.45f)
                                                        )
                                                    )
                                                )
                                        )
                                    } else {
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
                                    }

                                    // Format badge follows the artwork's top-right corner.
                                    Surface(
                                        shape = RoundedCornerShape(topEnd = 28.dp, bottomStart = 12.dp),
                                        color = animatedPrimary.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .clickable { viewModel.setShowAudioSpecs(true) }
                                    ) {
                                        Text(
                                            text = if (song.isHiRes) "HI-RES" else song.format,
                                            color = animatedPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 7.dp)
                                        )
                                    }

                                    if (playerState.isLoadingOnlineStream && song.format.contains("YouTube", ignoreCase = true)) {
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color.Black.copy(alpha = 0.72f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "Đang tải thông tin nhạc",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Lyrics area adapts to the available device height; the outer player can scroll on compact screens.
                    val lyricsListState = rememberLazyListState()
                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0 && activeIndex < parsedLyrics.lines.size) {
                            lyricsListState.animateScrollToItem(maxOf(0, activeIndex - 1))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lyricsPanelHeight),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    parsedLyrics.sourceName == "Gemini AI" && parsedLyrics.timingSource != null -> "Lời Karaoke · AI ước tính"
                                    parsedLyrics.sourceName == "Gemini AI" -> "Lời bài hát · Gemini AI"
                                    hasSyncedLyrics -> "Lời Karaoke đồng bộ"
                                    else -> "Lời bài hát · chưa đồng bộ"
                                },
                                color = animatedPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (parsedLyrics.sourceName == "Gemini AI") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NeonViolet.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonViolet.copy(alpha = 0.65f))
                                ) {
                                    Text(
                                        "AI",
                                        color = NeonViolet,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            val context = LocalContext.current
                            var showSearchMenu by remember { mutableStateOf(false) }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box {
                                    TextButton(
                                        onClick = { showSearchMenu = true },
                                        enabled = !uiState.isSearchingLyrics,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        if (uiState.isSearchingLyrics) {
                                            CircularProgressIndicator(color = NeonPink, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                        } else {
                                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonPink)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tìm Lời", fontSize = 11.sp, color = NeonPink)
                                    }

                                    DropdownMenu(
                                        expanded = showSearchMenu,
                                        onDismissRequest = { showSearchMenu = false },
                                        modifier = Modifier.background(DarkSurfaceElevated)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = NeonPink, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Tìm trên LRCLIB, sau đó Gemini AI có nguồn", color = TextPrimary, fontSize = 12.sp)
                                                }
                                            },
                                            onClick = {
                                                showSearchMenu = false
                                                viewModel.searchLyricsOnline(song)
                                            }
                                        )
                                        if (!song.lyrics.isNullOrBlank()) DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("AI ước tính mốc từ lời hiện có", color = TextPrimary, fontSize = 12.sp)
                                                }
                                            },
                                            onClick = {
                                                showSearchMenu = false
                                                viewModel.alignLyricsWithGemini(song.lyrics.orEmpty())
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Tìm kiếm trên Google", color = TextPrimary, fontSize = 12.sp)
                                                }
                                            },
                                            onClick = {
                                                showSearchMenu = false
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
                                    if (uiState.isSearchingLyrics) {
                                        CircularProgressIndicator(
                                            color = animatedPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Đang tìm lời bài hát…", color = TextSecondary, fontSize = 13.sp)
                                    } else {
                                        Text("Chưa tìm thấy lời bài hát", color = TextSecondary, fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.searchLyricsOnline(song) },
                                        enabled = !uiState.isSearchingLyrics,
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = AccentContent),
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(parsedLyrics.lines) { index, line ->
                                    val isActive = index == activeIndex
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isActive) animatedPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.5f)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (line.timeMs >= 0) {
                                                    viewModel.seekTo(line.timeMs)
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = line.text,
                                            color = if (isActive) TextPrimary else TextSecondary.copy(alpha = 0.72f),
                                            fontSize = if (isActive) { if (compactWidth) 14.sp else 16.sp } else { if (compactWidth) 12.sp else 14.sp },
                                            lineHeight = if (isActive) { if (compactWidth) 21.sp else 24.sp } else { if (compactWidth) 18.sp else 21.sp },
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !isLyricsMode,
                enter = fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Keep the song title to two lines so longer Vietnamese titles remain readable.
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                lineHeight = 21.sp,
                                letterSpacing = (-0.2).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                            .height(46.dp)
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
                                            color = TextPrimary,
                                            fontSize = 14.sp,
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

                    // Live or simulated audio wave visualization
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                    ) {
                        AudioSpectrumVisualizer(
                            isPlaying = playerState.isPlaying,
                            audioSessionId = playerState.audioSessionId,
                            hasAudioCapturePermission = hasAudioCapturePermission,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            customColors = listOf(animatedPrimary, animatedSecondary, animatedPrimary),
                            style = uiState.visualizerStyle,
                            onToggleStyle = { viewModel.toggleVisualizerStyle() },
                            onSelectStyle = { viewModel.setVisualizerStyle(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

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
                    .height(48.dp)
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
                        tint = if (playerState.isShuffle) animatedPrimary else TextMuted,
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
                        .size(68.dp)
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
                        if (playerState.isLoadingOnlineStream) {
                            CircularProgressIndicator(
                                color = PlayButtonContent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Tạm dừng" else "Phát",
                                tint = PlayButtonContent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
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

            AnimatedVisibility(
                visible = !isLyricsMode,
                enter = fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))

                    // DEDICATED QUICK CONTROLS BAR: SPEED, EQ, SLEEP TIMER, FAVORITE
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
                                .height(44.dp)
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
                                .height(44.dp)
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
                                .height(44.dp)
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

                        // 4. YÊU THÍCH
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (song.isFavorite) NeonPink.copy(alpha = 0.16f) else DarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (song.isFavorite) NeonPink.copy(alpha = 0.7f) else DarkBorder
                            ),
                            modifier = Modifier
                                .weight(1.05f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.toggleFavorite(song) }
                                .testTag("now_playing_favorite_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (song.isFavorite) "Bỏ yêu thích" else "Thêm vào yêu thích",
                                    tint = if (song.isFavorite) NeonPink else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (song.isFavorite) "Đã thích" else "Yêu thích",
                                    color = if (song.isFavorite) NeonPink else TextPrimary,
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
                }
            }

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
                colors = ButtonDefaults.buttonColors(containerColor = ElectricAzure, contentColor = AccentContent),
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
                                    color = if (isSelected) AccentContent else TextPrimary,
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

@Composable
private fun NowPlayingModeSelector(
    mode: NowPlayingDisplayMode,
    accent: Color,
    onModeChange: (NowPlayingDisplayMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .widthIn(max = 500.dp)
            .height(48.dp)
            .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
            .testTag("now_playing_mode_selector")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NowPlayingModeOption(
                label = "Ảnh bìa",
                icon = Icons.Default.Album,
                selected = mode == NowPlayingDisplayMode.ALBUM_ART,
                accent = accent,
                testTag = "now_playing_mode_album",
                modifier = Modifier.weight(1f),
                onClick = { onModeChange(NowPlayingDisplayMode.ALBUM_ART) }
            )
            NowPlayingModeOption(
                label = "Lời bài hát",
                icon = Icons.Default.Lyrics,
                selected = mode == NowPlayingDisplayMode.FULL_LYRICS,
                accent = accent,
                testTag = "now_playing_mode_lyrics",
                modifier = Modifier.weight(1f),
                onClick = { onModeChange(NowPlayingDisplayMode.FULL_LYRICS) }
            )
        }
    }
}

@Composable
private fun NowPlayingModeOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "mode_option_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else TextSecondary,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "mode_option_content"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.65f)) else null,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
