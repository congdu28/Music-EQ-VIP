package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.equalizer.EqualizerBand
import com.example.equalizer.EqualizerState
import com.example.model.EqualizerPreset
import com.example.player.PlayerUiState
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    equalizerState: EqualizerState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showSoundEffects by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .musicScreenBackground()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.setTab(if (playerState.currentSong != null) com.example.ui.MainTab.NOW_PLAYING else com.example.ui.MainTab.SETTINGS)
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Bộ chỉnh âm (EQ)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Tạo chất âm theo sở thích", color = TextSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = equalizerState.isEnabled,
                onCheckedChange = { viewModel.toggleEqualizerEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentContent,
                    checkedTrackColor = NeonCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.testTag("eq_master_switch")
            )
        }

        // Keep reset and save actions together in a compact panel.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.resetEqualizerToFlat() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("eq_reset_flat_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mặc định", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = { viewModel.setShowSavePreset(true) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("eq_save_preset_button")
                ) {
                    Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu Preset", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Preset Chips Horizontal Row
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Cài đặt sẵn (Presets)",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            val standardPresets = listOf(
                Pair("Mặc định (Flat)", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                Pair("Tăng âm trầm (Bass Boost)", listOf(800, 650, 500, 300, 100, 0, 0, 100, 200, 250)),
                Pair("Nhạc Pop", listOf(-100, 100, 250, 400, 500, 400, 200, 0, 200, 350)),
                Pair("Nhạc Rock", listOf(500, 400, 300, 100, -100, 0, 200, 400, 550, 600)),
                Pair("Nhạc Điện Tử EDM", listOf(750, 650, 400, 100, -100, 200, 350, 500, 650, 750)),
                Pair("Acoustic / Vocal", listOf(-250, -150, 0, 200, 400, 550, 450, 300, 150, 0)),
                Pair("Nhạc Jazz", listOf(300, 250, 150, 200, 0, -100, 0, 150, 250, 350)),
                Pair("Nhạc Cổ Điển", listOf(400, 300, 200, 150, 0, -100, 0, 200, 300, 400))
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(standardPresets) { (name, gains) ->
                    val isSelected = equalizerState.activePresetName == name
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) NeonCyan else DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) NeonCyan else DarkBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                val bass = if (name.contains("Bass")) 600 else if (name.contains("EDM")) 500 else 150
                                val virt = if (name.contains("Acoustic") || name.contains("Jazz")) 350 else 150
                                viewModel.equalizerManager.applyPreset(name, gains, bass, virt)
                            }
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) AccentContent else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                // Custom user saved presets
                items(uiState.presets.filter { it.isCustom }) { preset ->
                    val isSelected = equalizerState.activePresetName == preset.name
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) NeonPurple else DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) NeonPurple else DarkBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                viewModel.applyPreset(preset)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else NeonPurple,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = preset.name,
                                color = if (isSelected) Color.White else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Graphic Equalizer Bands Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "10 Dải Tần Âm Thanh",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NeonCyan.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "10 BANDS",
                                    color = NeonCyan,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Text(
                                text = "±12 dB",
                                color = TextHighlight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        "Kéo núm tròn để tăng hoặc giảm mức của từng dải",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight().width(34.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("+12dB", color = TextSecondary, fontSize = 9.sp, maxLines = 1)
                        Text("0dB", color = TextSecondary, fontSize = 9.sp, maxLines = 1)
                        Text("-12dB", color = TextSecondary, fontSize = 9.sp, maxLines = 1)
                    }
                    androidx.compose.foundation.Canvas(Modifier.weight(1f).fillMaxHeight()) {
                        val bands = equalizerState.bands
                        if (bands.isNotEmpty()) {
                            val left = 4.dp.toPx()
                            val right = size.width - 4.dp.toPx()
                            val top = 8.dp.toPx()
                            val bottom = size.height - 8.dp.toPx()
                            val zeroY = (top + bottom) / 2f
                            val points = bands.mapIndexed { index, band ->
                                val x = left + (right - left) * index / (bands.size - 1).coerceAtLeast(1)
                                val gain = band.levelDb.coerceIn(-12f, 12f)
                                Offset(x, zeroY - (gain / 12f) * ((bottom - top) / 2f))
                            }
                            listOf(top, zeroY, bottom).forEach { y ->
                                drawLine(DarkBorder, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                            }
                            points.forEach { point ->
                                drawLine(DarkBorder.copy(alpha = 0.55f), Offset(point.x, top), Offset(point.x, bottom), strokeWidth = 1.dp.toPx())
                            }
                            val curve = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (index in 0 until points.lastIndex) {
                                    val current = points[index]
                                    val next = points[index + 1]
                                    val midX = (current.x + next.x) / 2f
                                    cubicTo(midX, current.y, midX, next.y, next.x, next.y)
                                }
                            }
                            val fill = Path().apply {
                                addPath(curve)
                                lineTo(points.last().x, zeroY)
                                lineTo(points.first().x, zeroY)
                                close()
                            }
                            drawPath(
                                fill,
                                brush = Brush.verticalGradient(
                                    listOf(NeonViolet.copy(alpha = if (equalizerState.isEnabled) 0.36f else 0.12f), NeonCyan.copy(alpha = 0.03f)),
                                    startY = top,
                                    endY = bottom
                                )
                            )
                            drawPath(
                                curve,
                                brush = Brush.horizontalGradient(listOf(NeonCyan, NeonViolet, NeonPink)),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            points.forEach { point ->
                                drawCircle(Color.White, radius = 4.dp.toPx(), center = point)
                                drawCircle(NeonViolet, radius = 2.1.dp.toPx(), center = point)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Keep each fader wide enough to drag comfortably on a phone.
                val bandScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(206.dp)
                        .horizontalScroll(bandScrollState)
                        .testTag("eq_bands_scroll"),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    equalizerState.bands.forEach { band ->
                        SingleBandFader(
                            band = band,
                            isEnabled = equalizerState.isEnabled,
                            onLevelChange = { newLevel ->
                                viewModel.updateBandLevel(band.index, newLevel)
                            },
                            modifier = Modifier.width(36.dp).testTag("eq_band_${band.index}")
                        )
                    }
                }
                Text(
                    "Dùng mũi tên hoặc kéo thanh để xem thêm dải tần",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                EqHorizontalScrollbar(scrollState = bandScrollState)
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { showSoundEffects = !showSoundEffects }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = NeonViolet.copy(alpha = 0.15f), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hiệu ứng bổ trợ", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Bass, âm vòm, limiter, vang và loudness", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    imageVector = if (showSoundEffects) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showSoundEffects) "Thu gọn hiệu ứng" else "Mở hiệu ứng",
                    tint = TextSecondary
                )
            }
        }

        AnimatedVisibility(visible = showSoundEffects) {
        // Sound Enhancements: Bass Boost & 3D Virtualizer
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bass Boost Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonPurple.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = NeonPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Tăng cường âm trầm (Bass Boost)",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Tạo độ nảy và uy lực cho màng loa",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val bassPercent = (equalizerState.bassBoostStrength / 10).coerceIn(0, 100)
                        Text(
                            text = "$bassPercent%",
                            color = NeonPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = equalizerState.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = equalizerState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPurple,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("bass_boost_slider")
                    )
                }

                HorizontalDivider(color = DarkBorder)

                // 3D Virtualizer Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonCyan.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SurroundSound,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Âm thanh vòm 3D (Virtualizer)",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Mở rộng không gian âm trường đa chiều",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val virtPercent = (equalizerState.virtualizerStrength / 10).coerceIn(0, 100)
                        Text(
                            text = "$virtPercent%",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = equalizerState.virtualizerStrength.toFloat(),
                        onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = equalizerState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("virtualizer_slider")
                    )
                }

                HorizontalDivider(color = DarkBorder)

                // Pre-Amp Limiter Gain (-12dB to 0dB)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonGreen.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Pre-Amp Limiter (Chống rè loa)",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Hạ mức tín hiệu vào để tránh vỡ âm khi đẩy bass cao",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val preAmpDb = equalizerState.preAmpLevelMilliBels / 100f
                        Text(
                            text = String.format(java.util.Locale.US, "%.1fdB", preAmpDb),
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.widthIn(min = 52.dp),
                            textAlign = TextAlign.End
                        )
                    }

                    Slider(
                        value = (equalizerState.preAmpLevelMilliBels / 100f),
                        onValueChange = { viewModel.setPreAmpLevel(it) },
                        valueRange = -12f..0f,
                        enabled = equalizerState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreen,
                            activeTrackColor = NeonGreen,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = DarkBorder)

                // Loudness Enhancer (+0dB to +8dB)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonPink.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = NeonPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Khuếch đại âm lượng (Loudness Enhancer)",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Khuếch đại phần cứng cho tai nghe âm lượng nhỏ",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        val loudDb = String.format(java.util.Locale.US, "+%.1fdB", equalizerState.loudnessBoostMilliBels / 100f)
                        Text(
                            text = loudDb,
                            color = NeonPink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = equalizerState.loudnessBoostMilliBels.toFloat(),
                        onValueChange = { viewModel.setLoudnessBoost(it.toInt()) },
                        valueRange = 0f..800f,
                        enabled = equalizerState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPink,
                            activeTrackColor = NeonPink,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = DarkBorder)

                // Acoustic Space Reverb Presets
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonAmber.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MeetingRoom,
                                    contentDescription = null,
                                    tint = NeonAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Hiệu ứng không gian (Preset Reverb)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Mô phỏng độ vang của phòng nhạc hoặc khán phòng",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    val reverbOptions = listOf(
                        Pair(0.toShort(), "Tắt vang"),
                        Pair(1.toShort(), "Phòng nhỏ"),
                        Pair(2.toShort(), "Phòng vừa"),
                        Pair(3.toShort(), "Hội trường"),
                        Pair(4.toShort(), "Sân khấu")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reverbOptions) { (id, label) ->
                            val isSelected = equalizerState.reverbPreset == id
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) NeonAmber else DarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) NeonAmber else DarkBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = equalizerState.isEnabled) {
                                        viewModel.setReverbPreset(id)
                                    }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) AccentContent else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SingleBandFader(
    band: EqualizerBand,
    isEnabled: Boolean,
    onLevelChange: (Short) -> Unit,
    modifier: Modifier = Modifier
) {
    var componentHeightPx by remember { mutableStateOf(1f) }
    val density = LocalDensity.current

    val minRange = band.minLevelMilliBels.toFloat()
    val maxRange = band.maxLevelMilliBels.toFloat()
    val totalRange = (maxRange - minRange).coerceAtLeast(1f)
    val fraction = ((band.levelMilliBels.toFloat() - minRange) / totalRange).coerceIn(0f, 1f)
    val componentHeightDp = with(density) { componentHeightPx.toDp() }
    val thumbSize = 18.dp
    val thumbTravel = (componentHeightDp - thumbSize).coerceAtLeast(0.dp)
    val activeFillHeight = thumbTravel * kotlin.math.abs(fraction - 0.5f)

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Gain Readout (+3.5 dB)
        val dbFormatted = String.format(java.util.Locale.US, "%+.1fdB", band.levelDb)
        Text(
            text = dbFormatted,
            color = if (isEnabled && band.levelMilliBels != 0.toShort()) NeonCyan else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Track with Drag Handle
        Box(
            modifier = Modifier
                .width(32.dp)
                .weight(1f)
                .onSizeChanged { size -> componentHeightPx = size.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(minRange, maxRange) {
                    fun updateFromY(y: Float) {
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val newFraction = (1f - y / height).coerceIn(0f, 1f)
                        val newLevel = (minRange + totalRange * newFraction)
                            .roundToInt()
                            .coerceIn(band.minLevelMilliBels.toInt(), band.maxLevelMilliBels.toInt())
                            .toShort()
                        onLevelChange(newLevel)
                    }
                    detectDragGestures(
                        onDragStart = { offset -> updateFromY(offset.y) }
                    ) { change, _ ->
                        change.consume()
                        updateFromY(change.position.y)
                    }
                }
                .semantics {
                    contentDescription = "EQ ${band.centerFreqLabel}"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { value ->
                        val newLevel = (minRange + totalRange * value.coerceIn(0f, 1f))
                            .roundToInt()
                            .coerceIn(band.minLevelMilliBels.toInt(), band.maxLevelMilliBels.toInt())
                            .toShort()
                        onLevelChange(newLevel)
                        true
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background Vertical Rail
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkSurfaceVariant)
            )

            // Center Zero Mark Line
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .align(Alignment.Center)
                    .background(TextMuted.copy(alpha = 0.5f))
            )

            // Active Track Fill
            val fillHeightFraction = (fraction - 0.5f)
            if (activeFillHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(activeFillHeight)
                        .align(Alignment.Center)
                        .offset(y = if (fillHeightFraction > 0) -(activeFillHeight / 2) else activeFillHeight / 2)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                if (fillHeightFraction > 0) listOf(NeonCyan, ElectricAzure)
                                else listOf(NeonPink, DarkSurfaceVariant)
                            )
                        )
                )
            }

            // Draggable Thumb Knob
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isEnabled) (if (band.levelMilliBels != 0.toShort()) NeonCyan else TextPrimary) else DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (isEnabled) DarkBackground else TextMuted
                    ),
                    modifier = Modifier
                        .size(18.dp)
                        .offset(y = -(thumbTravel * fraction))
                ) {}
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Center Frequency Label (e.g. 60Hz, 1kHz)
        Text(
            text = band.centerFreqLabel,
            color = if (isEnabled) TextPrimary else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EqHorizontalScrollbar(scrollState: androidx.compose.foundation.ScrollState) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragAnchor by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { scope.launch { scrollState.scrollTo((scrollState.value - with(density) { 72.dp.roundToPx() }).coerceAtLeast(0)) } },
            enabled = scrollState.value > 0,
            modifier = Modifier.size(30.dp).testTag("eq_scroll_left")
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Cuộn sang trái", tint = if (scrollState.value > 0) NeonCyan else TextMuted)
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
            .pointerInput(scrollState.maxValue) {
                if (scrollState.maxValue <= 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { position ->
                        val viewport = size.width.toFloat().coerceAtLeast(1f)
                        val thumbWidth = (viewport * viewport / (viewport + scrollState.maxValue))
                            .coerceIn(32.dp.toPx(), viewport)
                        val maxThumbOffset = (viewport - thumbWidth).coerceAtLeast(1f)
                        val currentThumbOffset = scrollState.value.toFloat() / scrollState.maxValue * maxThumbOffset
                        dragAnchor = if (position.x in currentThumbOffset..(currentThumbOffset + thumbWidth)) {
                            position.x - currentThumbOffset
                        } else {
                            thumbWidth / 2f
                        }
                        val newThumbOffset = (position.x - dragAnchor).coerceIn(0f, maxThumbOffset)
                        scope.launch {
                            scrollState.scrollTo((newThumbOffset / maxThumbOffset * scrollState.maxValue).roundToInt())
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    val viewport = size.width.toFloat().coerceAtLeast(1f)
                    val thumbWidth = (viewport * viewport / (viewport + scrollState.maxValue))
                        .coerceIn(32.dp.toPx(), viewport)
                    val maxThumbOffset = (viewport - thumbWidth).coerceAtLeast(1f)
                    val newThumbOffset = (change.position.x - dragAnchor).coerceIn(0f, maxThumbOffset)
                    scope.launch {
                        scrollState.scrollTo((newThumbOffset / maxThumbOffset * scrollState.maxValue).roundToInt())
                    }
                }
            }
            .testTag("eq_bands_scrollbar")
        ) {
            val trackHeight = 7.dp.toPx()
            val thumbHeight = 11.dp.toPx()
            val centerY = size.height / 2f
            drawRoundRect(
                color = DarkSurfaceVariant,
                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2),
                size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight)
            )

            val thumbWidth = if (scrollState.maxValue == 0) size.width else
                (size.width * size.width / (size.width + scrollState.maxValue)).coerceIn(32.dp.toPx(), size.width)
            val thumbOffset = if (scrollState.maxValue == 0) 0f else
                scrollState.value.toFloat() / scrollState.maxValue * (size.width - thumbWidth)
            drawRoundRect(
                color = NeonCyan.copy(alpha = if (scrollState.maxValue == 0) 0.55f else 1f),
                topLeft = androidx.compose.ui.geometry.Offset(thumbOffset, centerY - thumbHeight / 2),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbHeight)
            )
        }

        IconButton(
            onClick = { scope.launch { scrollState.scrollTo((scrollState.value + with(density) { 72.dp.roundToPx() }).coerceAtMost(scrollState.maxValue)) } },
            enabled = scrollState.value < scrollState.maxValue,
            modifier = Modifier.size(30.dp).testTag("eq_scroll_right")
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Cuộn sang phải", tint = if (scrollState.value < scrollState.maxValue) NeonCyan else TextMuted)
        }
    }
}
