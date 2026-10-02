package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.equalizer.EqualizerState
import com.example.player.PlayerUiState
import com.example.ui.MainTab
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    equalizerState: EqualizerState,
    modifier: Modifier = Modifier
) {
    // Back gesture returns to Library
    BackHandler {
        viewModel.setTab(MainTab.LIBRARY)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Hero Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NeonViolet.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Headphones, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Nhịp Điệu Hi-Res Player", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Trình phát nhạc Hi-Res Lossless & Bộ chỉnh âm 5 dải", color = TextSecondary, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ElectricAzure.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricAzure)
                ) {
                    Text(
                        text = "Lossless 24-bit/96kHz Certified",
                        color = ElectricAzure,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Section: Định dạng âm thanh chất lượng cao hỗ trợ
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Định dạng âm thanh chất lượng cao hỗ trợ", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                val formats = listOf(
                    "FLAC (Lossless 24/96)",
                    "WAV (32-bit Float PCM)",
                    "ALAC (Apple Lossless)",
                    "AAC (High Bitrate 320k)",
                    "MP3 (320kbps Studio)",
                    "OGG / Opus (Hi-Fi Stream)"
                )

                formats.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { formatName ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(formatName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Bộ nhớ & Quét thư viện
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Thư viện nhạc ngoại tuyến", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                SettingItemRow(
                    icon = Icons.Default.FolderOpen,
                    iconTint = NeonCyan,
                    title = "Quét lại toàn bộ bộ nhớ",
                    subtitle = "Tìm kiếm file FLAC, WAV, MP3 mới tải về máy",
                    onClick = { viewModel.scanDeviceAudio() }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkBorder)

                SettingItemRow(
                    icon = Icons.Default.Bedtime,
                    iconTint = NeonPurple,
                    title = "Hẹn giờ tắt nhạc (Sleep Timer)",
                    subtitle = if (playerState.isSleepTimerActive) "Đang hẹn giờ: ${playerState.sleepTimerRemainingSeconds / 60} phút còn lại" else "Tự động dừng phát khi đi ngủ",
                    onClick = { viewModel.setShowSleepTimer(true) }
                )
            }
        }

        // Section: Chuyển nhạc mượt mà (Crossfade)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chuyển nhạc mượt mà (Crossfade)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (playerState.isCrossfadeEnabled) "Hòa trộn âm thanh ${String.format("%.1f", playerState.crossfadeDurationSeconds)}s giữa các bài hát" else "Đã tắt (Chuyển bài lập tức)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = playerState.isCrossfadeEnabled,
                        onCheckedChange = { viewModel.setCrossfadeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonCyan)
                    )
                }

                if (playerState.isCrossfadeEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Thời gian hòa âm (Fade In/Out): ${String.format("%.1fs", playerState.crossfadeDurationSeconds)}",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = playerState.crossfadeDurationSeconds,
                        onValueChange = { viewModel.setCrossfadeDuration(it) },
                        valueRange = 0.5f..5.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5f, 1.0f, 2.0f, 3.0f, 5.0f).forEach { sec ->
                            val isSel = kotlin.math.abs(playerState.crossfadeDurationSeconds - sec) < 0.2f
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) NeonCyan else DarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) NeonCyan else DarkBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.setCrossfadeDuration(sec) }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${sec.toInt().takeIf { it.toFloat() == sec } ?: sec}s",
                                        color = if (isSel) DarkBackground else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Cấu hình EQ & Bộ xử lý âm thanh
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bộ chỉnh âm Equalizer", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bật bộ lọc Equalizer", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (equalizerState.isEnabled) "Đang áp dụng: ${equalizerState.activePresetName}" else "Đã tắt bộ lọc", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = equalizerState.isEnabled,
                        onCheckedChange = { viewModel.toggleEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonCyan)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkBorder)

                SettingItemRow(
                    icon = Icons.Default.Tune,
                    iconTint = NeonCyan,
                    title = "Tùy chỉnh dải tần & Hiệu ứng",
                    subtitle = "Mở bảng điều khiển Equalizer, Bass Boost & Âm vòm 3D",
                    onClick = { viewModel.setTab(MainTab.EQUALIZER) }
                )
            }
        }

        // Section: Cấu hình Gemini AI & Tìm kiếm Lời bài hát
        var apiKeyInput by remember(uiState.geminiApiKey) { mutableStateOf(uiState.geminiApiKey) }
        var isEditingKey by remember { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonViolet.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Gemini AI & Tìm lời", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tạo lời Karaoke LRC tự động bằng AI", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { 
                        apiKeyInput = it 
                        isEditingKey = true
                    },
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonViolet,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (apiKeyInput.isNotBlank()) "Đã cấu hình API Key" else "Chưa có API Key",
                        color = if (apiKeyInput.isNotBlank()) NeonCyan else TextMuted,
                        fontSize = 11.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (apiKeyInput != com.example.lyrics.OnlineLyricsService.DEFAULT_GEMINI_API_KEY) {
                            TextButton(
                                onClick = {
                                    apiKeyInput = com.example.lyrics.OnlineLyricsService.DEFAULT_GEMINI_API_KEY
                                    viewModel.setGeminiApiKey(com.example.lyrics.OnlineLyricsService.DEFAULT_GEMINI_API_KEY)
                                    isEditingKey = false
                                }
                            ) {
                                Text("Khôi phục mặc định", fontSize = 11.sp, color = TextSecondary)
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.setGeminiApiKey(apiKeyInput)
                                isEditingKey = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lưu Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: Thông tin ứng dụng
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Thông tin & Giấy phép", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                InfoRow("Phiên bản", "1.0.3 (Gemini AI & Google Search)")
                InfoRow("Ngôn ngữ", "Tiếng Việt (100% Native)")
                InfoRow("Bộ xử lý âm thanh", "Android AudioFX + Native DSP")
                InfoRow("Trình phân tích lời", "Karaoke LRC Synchronizer v2.0 + Gemini AI")
            }
        }
    }
}

@Composable
private fun SettingItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconTint.copy(alpha = 0.15f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }

        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
