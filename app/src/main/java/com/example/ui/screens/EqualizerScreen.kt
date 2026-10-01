package com.example.ui.screens

import android.media.audiofx.PresetReverb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.equalizer.EqualizerBand
import com.example.equalizer.EqualizerState
import com.example.equalizer.SpeakerProfile
import com.example.player.PlayerUiState
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.components.AudioSpectrumVisualizer
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

    LaunchedEffect(Unit) {
        viewModel.refreshAudioDevice()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master & System-Wide EQ Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (equalizerState.isEnabled && equalizerState.isSystemWide) NeonCyan else DarkBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Master Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (equalizerState.isEnabled) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (equalizerState.isEnabled) NeonCyan else TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Bộ Chỉnh Âm (Equalizer)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (equalizerState.isEnabled) NeonGreen else TextMuted)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (equalizerState.isEnabled) "Đang hoạt động (${equalizerState.activePresetName})" else "Đã tắt bộ xử lý âm thanh",
                                    color = if (equalizerState.isEnabled) NeonCyan else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Switch(
                        checked = equalizerState.isEnabled,
                        onCheckedChange = { viewModel.toggleEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("eq_master_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = DarkBorder)

                // SYSTEM WIDE EQ SWITCH (Key Feature requested by user!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "EQ Toàn Hệ Thống & App Thứ 3",
                                color = if (equalizerState.isSystemWide && equalizerState.isEnabled) NeonCyan else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (equalizerState.isSystemWide) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                border = if (equalizerState.isSystemWide) androidx.compose.foundation.BorderStroke(0.8.dp, NeonCyan) else null
                            ) {
                                Text(
                                    text = if (equalizerState.isSystemWide) "GLOBAL SESSION 0" else "APP ONLY",
                                    color = if (equalizerState.isSystemWide) NeonCyan else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Áp dụng hiệu ứng âm thanh cho toàn bộ loa thiết bị và ứng dụng thứ 3 (YouTube, Spotify, Soundcloud, TikTok, Games...)",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = equalizerState.isSystemWide,
                        onCheckedChange = { viewModel.toggleSystemWideEq(it) },
                        enabled = equalizerState.isEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("system_wide_eq_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Audio Output Device Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    equalizerState.currentOutputDeviceName.contains("Bluetooth", true) -> Icons.Default.BluetoothAudio
                                    equalizerState.currentOutputDeviceName.contains("Tai nghe", true) -> Icons.Default.Headphones
                                    else -> Icons.Default.VolumeUp
                                },
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Đầu ra âm thanh: ${equalizerState.currentOutputDeviceName}",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshAudioDevice() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Cập nhật thiết bị",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Tối Ưu Cho Loa Toàn Hệ Thống (Speaker Tuning & Loudness Booster Engine)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonPink.copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.SpeakerGroup, contentDescription = null, tint = NeonPink, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tối Ưu Loa Toàn Hệ Thống", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NeonPink.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, NeonPink)
                    ) {
                        Text(
                            text = "SPEAKER PRO",
                            color = NeonPink,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Chọn cấu hình phù hợp với loại loa bạn đang sử dụng:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Speaker Profiles
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeakerProfile.values().forEach { profile ->
                        val isSelected = equalizerState.speakerProfile == profile
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) NeonViolet.copy(alpha = 0.25f) else DarkSurfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, NeonCyan) else androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setSpeakerProfile(profile) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (profile) {
                                        SpeakerProfile.PHONE_SPEAKER -> Icons.Default.Smartphone
                                        SpeakerProfile.BLUETOOTH_SPEAKER -> Icons.Default.Speaker
                                        SpeakerProfile.HEADPHONES -> Icons.Default.Headphones
                                        SpeakerProfile.CUSTOM -> Icons.Default.Tune
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.displayName,
                                        color = if (isSelected) NeonCyan else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = profile.subtitle,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Speaker Loudness Booster (Hardware LoudnessEnhancer)
                val loudnessDb = equalizerState.loudnessEnhancerGainMb / 100f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Khuếch Đại Âm Lượng Loa", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NeonPink.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "HARDWARE",
                                    color = NeonPink,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text("Tăng âm lượng phần cứng loa mà không làm vỡ âm sắc", color = TextSecondary, fontSize = 11.sp)
                    }
                    Text(
                        text = "+${String.format("%.1f", loudnessDb)} dB",
                        color = NeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Slider(
                    value = equalizerState.loudnessEnhancerGainMb.toFloat(),
                    onValueChange = { viewModel.setLoudnessEnhancer(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = equalizerState.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPink,
                        activeTrackColor = NeonPink,
                        inactiveTrackColor = DarkBorder
                    ),
                    modifier = Modifier.testTag("loudness_enhancer_slider")
                )

                // Anti-Clipping Protection Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bảo Vệ Màng Loa (Anti-Clipping Limiter)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Tự động nén đỉnh tín hiệu khi mở loa lớn, ngăn ngừa rè và méo âm", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = equalizerState.isAntiClippingEnabled,
                        onCheckedChange = { viewModel.toggleAntiClipping(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonCyan)
                    )
                }
            }
        }

        // Live Audio Spectrum Visualizer
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Phổ Âm Thanh Thời Gian Thực", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = if (playerState.isPlaying) "ĐANG PHÁT" else "TẠM DỪNG",
                        color = if (playerState.isPlaying) NeonGreen else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                AudioSpectrumVisualizer(
                    isPlaying = playerState.isPlaying && equalizerState.isEnabled,
                    bands = equalizerState.bands
                )
            }
        }

        // Presets Horizontal Carousel + Save Custom Preset Button
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cấu Hình Âm Thanh (Presets)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(
                    onClick = { viewModel.setShowSavePreset(true) },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lưu cấu hình", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(uiState.presets) { preset ->
                    val isSelected = equalizerState.activePresetName == preset.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.applyPreset(preset) },
                        label = {
                            Text(
                                text = preset.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) DarkBackground else TextPrimary,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextPrimary,
                            selectedLabelColor = DarkBackground
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = DarkBorder,
                            selectedBorderColor = NeonCyan
                        )
                    )
                }
            }
        }

        // 10-Band Studio Graphic Equalizer with Role Descriptions & Native Vertical Sliders
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bộ Chỉnh 10 Dải Tần Âm Thanh", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Chạm trượt để chỉnh dB • Chạm 2 lần để về 0dB", color = TextSecondary, fontSize = 11.sp)
                    }

                    // Reset all bands to 0 dB flat button
                    TextButton(
                        onClick = {
                            val preset = uiState.presets.firstOrNull { it.name.contains("Mặc định") || it.name.contains("Flat") }
                            if (preset != null) {
                                viewModel.applyPreset(preset)
                            } else {
                                equalizerState.bands.forEach { b -> viewModel.updateBandLevel(b.index, 0) }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đặt lại 0dB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Smooth horizontal scroll of 10 bands with dedicated vertical gesture slider
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    items(equalizerState.bands, key = { it.index }) { band ->
                        VerticalEqualizerBandSlider(
                            band = band,
                            isEnabled = equalizerState.isEnabled,
                            onLevelChange = { newLevelMb ->
                                viewModel.updateBandLevel(band.index, newLevelMb)
                            }
                        )
                    }
                }
            }
        }

        // Bass Boost & 3D Virtualizer Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bass Boost
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tăng Âm Trầm", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${equalizerState.bassBoostStrength / 10}%", color = NeonPink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text("Bass Booster", color = TextMuted, fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = equalizerState.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = equalizerState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPink,
                            activeTrackColor = NeonPink,
                            inactiveTrackColor = DarkBorder
                        ),
                        modifier = Modifier.testTag("bass_boost_slider")
                    )
                }
            }

            // 3D Virtualizer
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Âm Vòm 3D", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${equalizerState.virtualizerStrength / 10}%", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text("Spatial Audio", color = TextMuted, fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = equalizerState.virtualizerStrength.toFloat(),
                        onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = equalizerState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPurple,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = DarkBorder
                        ),
                        modifier = Modifier.testTag("virtualizer_slider")
                    )
                }
            }
        }

        // Reverb Environment
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hiệu Ứng Không Gian (Reverb Acoustic)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                val reverbOptions = listOf(
                    PresetReverb.PRESET_NONE to "Tắt",
                    PresetReverb.PRESET_SMALLROOM to "Phòng nhỏ",
                    PresetReverb.PRESET_MEDIUMROOM to "Phòng vừa",
                    PresetReverb.PRESET_LARGEHALL to "Đại sảnh",
                    PresetReverb.PRESET_PLATE to "Sân khấu"
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reverbOptions) { (presetVal, label) ->
                        val isSelected = equalizerState.reverbPreset == presetVal
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setReverbPreset(presetVal) },
                            enabled = equalizerState.isEnabled,
                            label = { Text(label, color = if (isSelected) DarkBackground else TextPrimary, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                containerColor = DarkSurfaceElevated
                            )
                        )
                    }
                }
            }
        }

        // Hi-Res Audio DSP processing toggles
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Xử Lý Âm Thanh Chất Lượng Cao (Hi-Res DSP)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("32-bit Float Audio Engine", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Khử nhiễu Dithering và tái tạo âm trường chi tiết", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = equalizerState.isHiResDspEnabled,
                        onCheckedChange = { viewModel.toggleHiResDsp(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonCyan)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DarkBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cân Bằng Âm Lượng (ReplayGain)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tự động chuẩn hóa âm lượng giữa các định dạng bài hát", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = equalizerState.isReplayGainEnabled,
                        onCheckedChange = { viewModel.toggleReplayGain(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonCyan)
                    )
                }
            }
        }

        // Testing Guide Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Mẹo kiểm tra EQ trên loa & app thứ 3", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Khi bật 'EQ Toàn Hệ Thống', bạn có thể mở YouTube, Spotify, Soundcloud hay TikTok. Âm thanh loa sẽ thay đổi ngay theo thiết lập EQ và Khuếch đại loa.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Custom Audiophile Vertical Slider for Graphic Equalizer.
 * Provides smooth, conflict-free vertical touch dragging, tap-to-set,
 * double-tap to reset to 0dB, center tick, bipolar gradient fill,
 * and descriptive acoustic frequency tag.
 */
@Composable
fun VerticalEqualizerBandSlider(
    band: EqualizerBand,
    isEnabled: Boolean,
    onLevelChange: (Short) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = band.levelDb
    val dbText = if (db > 0) "+${String.format("%.1f", db)}" else String.format("%.1f", db)
    val minMb = band.minLevelMilliBels.toFloat()
    val maxMb = band.maxLevelMilliBels.toFloat()
    val rangeMb = maxMb - minMb

    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(56.dp)
    ) {
        // dB Readout Text (fixed width to prevent layout jitter)
        Text(
            text = dbText,
            color = when {
                !isEnabled -> TextMuted
                db > 0.05f -> NeonPink
                db < -0.05f -> NeonCyan
                else -> TextSecondary
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Custom Vertical Track Box with instant touch response & conflict-free drag
        Box(
            modifier = Modifier
                .height(145.dp)
                .width(42.dp)
                .onSizeChanged { size ->
                    trackHeightPx = size.height.toFloat()
                }
                .pointerInput(band.index, isEnabled, minMb, maxMb) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            // Instant reset to 0 dB
                            onLevelChange(0)
                        },
                        onTap = { offset ->
                            val fraction = (1f - (offset.y / trackHeightPx)).coerceIn(0f, 1f)
                            val newMb = (minMb + fraction * rangeMb).roundToInt().coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
                            onLevelChange(newMb)
                        }
                    )
                }
                .pointerInput(band.index, isEnabled, minMb, maxMb) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val fraction = (1f - (offset.y / trackHeightPx)).coerceIn(0f, 1f)
                            val newMb = (minMb + fraction * rangeMb).roundToInt().coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
                            onLevelChange(newMb)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val currentFraction = (band.levelMilliBels - minMb) / rangeMb
                            val deltaFraction = -dragAmount / trackHeightPx
                            val newFraction = (currentFraction + deltaFraction).coerceIn(0f, 1f)
                            val newMb = (minMb + newFraction * rangeMb).roundToInt().coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
                            onLevelChange(newMb)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Track background bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurfaceVariant)
            )

            // Center 0dB horizontal indicator line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(2.dp)
                    .background(if (isEnabled) DarkBorder.copy(alpha = 0.9f) else Color.Transparent)
            )

            // Dynamic Bipolar Active Fill
            val currentFraction = ((band.levelMilliBels - minMb) / rangeMb).coerceIn(0f, 1f)
            val thumbYPercent = (1f - currentFraction) // 0f is top, 1f is bottom

            // Active bar from center (50%) to thumb
            if (isEnabled && (band.levelMilliBels != 0.toShort())) {
                val isBoost = band.levelMilliBels > 0
                val barTopPercent = if (isBoost) thumbYPercent else 0.5f
                val barHeightPercent = if (isBoost) (0.5f - thumbYPercent) else (thumbYPercent - 0.5f)

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(barHeightPercent.coerceAtLeast(0.01f))
                            .align(if (isBoost) Alignment.BottomCenter else Alignment.TopCenter)
                            .offset(y = if (isBoost) (-(0.5f * 145).dp) else ((0.5f * 145).dp))
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isBoost) listOf(NeonPink, NeonPurple) else listOf(NeonCyan, NeonCyanDim)
                                )
                            )
                    )
                }
            }

            // Draggable Thumb Knob
            val thumbOffset = ((thumbYPercent - 0.5f) * 145).dp
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isEnabled) {
                    if (db > 0) NeonPink else NeonCyan
                } else TextMuted,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (isEnabled) Color.White else DarkBorder
                ),
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .size(width = 34.dp, height = 20.dp)
                    .shadow(
                        elevation = if (isEnabled) 6.dp else 0.dp,
                        shape = RoundedCornerShape(10.dp),
                        spotColor = if (db > 0) NeonPink else NeonCyan
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(8.dp)
                                    .background(DarkBackground.copy(alpha = 0.7f))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Center Frequency Label (e.g. 31Hz, 1k, 16k)
        Text(
            text = band.centerFreqLabel,
            color = if (isEnabled) TextPrimary else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        // Descriptive Role Tag (fixed height container to eliminate vertical jitter)
        Box(modifier = Modifier.height(14.dp), contentAlignment = Alignment.Center) {
            Text(
                text = band.frequencyRole,
                color = TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
